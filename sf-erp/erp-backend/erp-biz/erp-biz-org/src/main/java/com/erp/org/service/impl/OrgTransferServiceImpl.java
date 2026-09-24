package com.erp.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.org.constant.OrgConstants;
import com.erp.org.dto.form.TransferForm;
import com.erp.org.dto.raw.CostBalanceRaw;
import com.erp.org.dto.raw.WarehouseRaw;
import com.erp.org.dto.request.TransferQuery;
import com.erp.org.dto.vo.TransferLineStatVO;
import com.erp.org.dto.vo.TransferVO;
import com.erp.org.entity.OrgFactory;
import com.erp.org.entity.OrgInvMovement;
import com.erp.org.entity.OrgTransfer;
import com.erp.org.entity.OrgTransferDetail;
import com.erp.org.entity.OrgTransferPrice;
import com.erp.org.mapper.OrgCostFlowMapper;
import com.erp.org.mapper.OrgFactoryMapper;
import com.erp.org.mapper.OrgInvMovementMapper;
import com.erp.org.mapper.OrgInvStockMapper;
import com.erp.org.mapper.OrgTransferDetailMapper;
import com.erp.org.mapper.OrgTransferMapper;
import com.erp.org.mapper.OrgTransferPriceMapper;
import com.erp.org.service.OrgTransferService;
import com.erp.org.support.OrgBizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 组织间调拨单服务实现。
 * <p>
 * 关键约束（不变量）：
 * <ol>
 *   <li>状态机单向：草稿 → 已审核 / 已取消；已审核单不可取消（红字冲销另立单据）；</li>
 *   <li>审核过账整体一个事务：调出仓条件 UPDATE 扣减（qty-?&gt;=0 防负库存，WHERE 带 deleted=0），
 *       影响行数 0 一律按「库存不足」抛 BusinessException 全单回滚（movement 不残留）；</li>
 *   <li>每行物料写两行 inv_movement（ORG_OUT 负 / ORG_IN 正），biz_no 与 remark 记 IT 单号；</li>
 *   <li>价格快照在保存时计算：成本单价取 cost_flow 最新运行余额（表未建成/无数据降级 0），
 *       结算单价按价目 RATE/FIXED 规则（无启用价目=成本）。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class OrgTransferServiceImpl implements OrgTransferService {

    /** 单价精度（与 cost_flow.unit_cost 对齐，6 位） */
    private static final int PRICE_SCALE = 6;
    /** 金额精度（规范：DECIMAL(18,2)，HALF_UP） */
    private static final int AMOUNT_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final OrgTransferMapper transferMapper;
    private final OrgTransferDetailMapper detailMapper;
    private final OrgTransferPriceMapper priceMapper;
    private final OrgFactoryMapper factoryMapper;
    private final OrgCostFlowMapper costFlowMapper;
    private final OrgInvStockMapper invStockMapper;
    private final OrgInvMovementMapper movementMapper;
    private final OrgBizNoGenerator bizNoGenerator;

    // ==================== 查询 ====================

    @Override
    public PageResult<TransferVO> page(TransferQuery query) {
        IPage<TransferVO> page = transferMapper.pageTransfer(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public TransferVO.Detail detail(Long id) {
        TransferVO head = transferMapper.selectHead(id);
        if (head == null) {
            throw BusinessException.ofKey("erp.org.transfer-not-exists", id);
        }
        List<TransferVO.LineVO> lines = transferMapper.selectLines(id);
        TransferVO.Detail detail = new TransferVO.Detail();
        detail.setId(head.getId());
        detail.setTransferNo(head.getTransferNo());
        detail.setFromFactoryId(head.getFromFactoryId());
        detail.setFromFactoryName(head.getFromFactoryName());
        detail.setFromWarehouseId(head.getFromWarehouseId());
        detail.setFromWarehouseName(head.getFromWarehouseName());
        detail.setToFactoryId(head.getToFactoryId());
        detail.setToFactoryName(head.getToFactoryName());
        detail.setToWarehouseId(head.getToWarehouseId());
        detail.setToWarehouseName(head.getToWarehouseName());
        detail.setStatus(head.getStatus());
        detail.setAuditTime(head.getAuditTime());
        detail.setRemark(head.getRemark());
        detail.setCreateTime(head.getCreateTime());
        detail.setLines(lines);
        detail.setLineCount((long) lines.size());
        detail.setTotalQty(lines.stream().map(TransferVO.LineVO::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        detail.setTotalAmount(lines.stream().map(TransferVO.LineVO::getSettleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return detail;
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TransferForm form) {
        validateRoute(form);
        List<OrgTransferDetail> details = buildDetails(form.getFromFactoryId(), form.getToFactoryId(),
                form.getLines());

        OrgTransfer transfer = buildHead(form);
        transfer.setTransferNo(bizNoGenerator.next(OrgConstants.PREFIX_TRANSFER));
        transfer.setStatus(OrgConstants.TRANSFER_DRAFT);
        transferMapper.insert(transfer);

        for (OrgTransferDetail detail : details) {
            detail.setTransferId(transfer.getId());
            detailMapper.insert(detail);
        }
        return transfer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TransferForm form) {
        requireDraft(id);
        validateRoute(form);
        List<OrgTransferDetail> details = buildDetails(form.getFromFactoryId(), form.getToFactoryId(),
                form.getLines());

        OrgTransfer transfer = buildHead(form);
        transfer.setId(id);
        transferMapper.updateById(transfer);

        // 明细整体替换：旧行逻辑删除后重算重插（价格快照随之刷新）
        detailMapper.delete(new LambdaQueryWrapper<OrgTransferDetail>()
                .eq(OrgTransferDetail::getTransferId, id));
        for (OrgTransferDetail detail : details) {
            detail.setTransferId(id);
            detailMapper.insert(detail);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        OrgTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw BusinessException.ofKey("erp.org.transfer-not-exists", id);
        }
        if (transfer.getStatus() != OrgConstants.TRANSFER_DRAFT) {
            throw BusinessException.ofKey("erp.org.audit-only-draft", transfer.getStatus());
        }
        List<OrgTransferDetail> lines = detailMapper.selectList(
                new LambdaQueryWrapper<OrgTransferDetail>()
                        .eq(OrgTransferDetail::getTransferId, id)
                        .orderByAsc(OrgTransferDetail::getId));
        if (lines.isEmpty()) {
            throw BusinessException.ofKey("erp.org.audit-no-lines");
        }
        for (OrgTransferDetail line : lines) {
            postOut(transfer, line);
            postIn(transfer, line);
        }
        // 条件更新兜底并发审核：另一事务已审掉此单时此处 0 行，抛异常回滚本次全部过账
        int updated = transferMapper.update(null, new LambdaUpdateWrapper<OrgTransfer>()
                .eq(OrgTransfer::getId, id)
                .eq(OrgTransfer::getStatus, OrgConstants.TRANSFER_DRAFT)
                .set(OrgTransfer::getStatus, OrgConstants.TRANSFER_AUDITED)
                .set(OrgTransfer::getAuditBy, SecurityUtils.getCurrentUserId())
                .set(OrgTransfer::getAuditTime, LocalDateTime.now()));
        if (updated == 0) {
            throw BusinessException.ofKey("erp.org.audit-status-changed");
        }
    }

    @Override
    public void cancel(Long id) {
        OrgTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw BusinessException.ofKey("erp.org.transfer-not-exists", id);
        }
        if (transfer.getStatus() != OrgConstants.TRANSFER_DRAFT) {
            throw BusinessException.ofKey("erp.org.cancel-only-draft");
        }
        int updated = transferMapper.update(null, new LambdaUpdateWrapper<OrgTransfer>()
                .eq(OrgTransfer::getId, id)
                .eq(OrgTransfer::getStatus, OrgConstants.TRANSFER_DRAFT)
                .set(OrgTransfer::getStatus, OrgConstants.TRANSFER_CANCELLED));
        if (updated == 0) {
            throw BusinessException.ofKey("erp.org.cancel-status-changed");
        }
    }

    @Override
    public List<TransferVO.LineVO> preview(TransferForm form) {
        validateRoute(form);
        List<OrgTransferDetail> details = buildDetails(form.getFromFactoryId(), form.getToFactoryId(),
                form.getLines());
        List<TransferVO.LineVO> result = new ArrayList<>(details.size());
        for (int i = 0; i < details.size(); i++) {
            OrgTransferDetail d = details.get(i);
            TransferForm.Line src = form.getLines().get(i);
            TransferVO.LineVO vo = new TransferVO.LineVO();
            vo.setMaterialId(src.getMaterialId());
            vo.setQty(src.getQty());
            vo.setCostPrice(d.getCostPrice());
            vo.setSettlePrice(d.getSettlePrice());
            vo.setSettleAmount(d.getSettleAmount());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<TransferLineStatVO> statisticsLine(LocalDate from, LocalDate to) {
        LocalDate begin = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(begin)) {
            throw BusinessException.ofKey("erp.org.stat-period-invalid");
        }
        return transferMapper.statisticsLine(begin, end);
    }

    // ==================== 过账原语 ====================

    /**
     * 调出仓扣库存 + ORG_OUT 流水。
     * <p>
     * 条件 UPDATE 带 qty-?&gt;=0 与 deleted=0；影响 0 行统一按库存不足抛异常，
     * 由 @Transactional 整体回滚（此前已过账的行一并撤销，无流水残留）。
     */
    private void postOut(OrgTransfer transfer, OrgTransferDetail line) {
        int rows = invStockMapper.deductStock(transfer.getFromWarehouseId(),
                line.getMaterialId(), line.getQty());
        if (rows == 0) {
            throw BusinessException.ofKey("erp.org.stock-not-enough-transfer", transfer.getFromWarehouseId(), line.getMaterialId(), line.getQty().stripTrailingZeros().toPlainString(), transfer.getTransferNo());
        }
        insertMovement(transfer, line, OrgConstants.BIZ_TYPE_ORG_OUT,
                line.getQty().negate(), OrgConstants.ORG_OUT_REMARK_PREFIX + transfer.getTransferNo());
    }

    /** 调入仓加库存（UPDATE 命中 0 行走 UPSERT 兜底）+ ORG_IN 流水 */
    private void postIn(OrgTransfer transfer, OrgTransferDetail line) {
        int rows = invStockMapper.increaseStock(transfer.getToWarehouseId(),
                line.getMaterialId(), line.getQty());
        if (rows == 0) {
            invStockMapper.upsertIncreaseStock(transfer.getToWarehouseId(),
                    line.getMaterialId(), line.getQty(), SecurityUtils.getCurrentUserId());
        }
        insertMovement(transfer, line, OrgConstants.BIZ_TYPE_ORG_IN,
                line.getQty(), OrgConstants.ORG_IN_REMARK_PREFIX + transfer.getTransferNo());
    }

    /** 按 inventory 模块约定写流水（qty 正入负出；审计列 MP 自动填充） */
    private void insertMovement(OrgTransfer transfer, OrgTransferDetail line,
                                String bizType, BigDecimal qty, String remark) {
        OrgInvMovement movement = new OrgInvMovement();
        movement.setBizType(bizType);
        movement.setBizId(transfer.getId());
        movement.setBizNo(transfer.getTransferNo());
        movement.setWarehouseId(OrgConstants.BIZ_TYPE_ORG_OUT.equals(bizType)
                ? transfer.getFromWarehouseId() : transfer.getToWarehouseId());
        movement.setMaterialId(line.getMaterialId());
        movement.setQty(qty);
        movement.setRemark(remark);
        movementMapper.insert(movement);
    }

    // ==================== 私有工具 ====================

    /**
     * 校验调拨线路：工厂不同且启用、仓库存在/启用/归属正确。
     */
    private void validateRoute(TransferForm form) {
        if (Objects.equals(form.getFromFactoryId(), form.getToFactoryId())) {
            throw BusinessException.ofKey("erp.org.same-factory-inter-org");
        }
        OrgFactory fromFactory = requireEnabledFactory(form.getFromFactoryId(), "调出");
        OrgFactory toFactory = requireEnabledFactory(form.getToFactoryId(), "调入");
        WarehouseRaw fromWh = requireEnabledWarehouse(form.getFromWarehouseId(), "调出");
        WarehouseRaw toWh = requireEnabledWarehouse(form.getToWarehouseId(), "调入");
        if (!Objects.equals(fromWh.getFactoryId(), fromFactory.getId())) {
            throw BusinessException.ofKey("erp.org.out-warehouse-not-in-factory", fromWh.getName(), fromFactory.getName());
        }
        if (!Objects.equals(toWh.getFactoryId(), toFactory.getId())) {
            throw BusinessException.ofKey("erp.org.in-warehouse-not-in-factory", toWh.getName(), toFactory.getName());
        }
        if (Objects.equals(form.getFromWarehouseId(), form.getToWarehouseId())) {
            throw BusinessException.ofKey("erp.org.same-warehouse-transfer");
        }
    }

    private OrgTransfer buildHead(TransferForm form) {
        OrgTransfer transfer = new OrgTransfer();
        transfer.setFromFactoryId(form.getFromFactoryId());
        transfer.setFromWarehouseId(form.getFromWarehouseId());
        transfer.setToFactoryId(form.getToFactoryId());
        transfer.setToWarehouseId(form.getToWarehouseId());
        transfer.setRemark(form.getRemark());
        return transfer;
    }

    /**
     * 依据表单行构建明细（价格快照计算核心）：同物料成本/价目在同一次构建内缓存去重查询。
     */
    private List<OrgTransferDetail> buildDetails(Long fromFactoryId, Long toFactoryId,
                                                 List<TransferForm.Line> lines) {
        Map<Long, BigDecimal> costCache = new HashMap<>();
        Map<Long, OrgTransferPrice> ruleCache = new HashMap<>();
        List<OrgTransferDetail> details = new ArrayList<>(lines.size());
        for (TransferForm.Line line : lines) {
            if (transferMapper.countMaterialExists(line.getMaterialId()) == 0) {
                throw BusinessException.ofKey("erp.org.line-material-not-exists", line.getMaterialId());
            }
            BigDecimal cost = costCache.computeIfAbsent(line.getMaterialId(), this::resolveCostPrice);
            OrgTransferPrice rule = ruleCache.computeIfAbsent(line.getMaterialId(),
                    materialId -> priceMapper.selectEffectiveRule(materialId, fromFactoryId, toFactoryId));
            BigDecimal settle = applyMarkup(cost, rule);

            OrgTransferDetail detail = new OrgTransferDetail();
            detail.setMaterialId(line.getMaterialId());
            detail.setQty(line.getQty());
            detail.setCostPrice(cost);
            detail.setSettlePrice(settle);
            detail.setSettleAmount(settle.multiply(line.getQty())
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
            details.add(detail);
        }
        return details;
    }

    /**
     * 调出方最近加权价 = cost_flow 最新运行余额 balance_cost / balance_qty（6 位 HALF_UP）。
     * <p>
     * cost_flow 表尚未建成（cost 模块 V801 未执行）或物料无成本数据时降级为 0，
     * 不阻断调拨录入（结算金额随之按 0 结算，见 docs/modules/org.md 说明）。
     */
    private BigDecimal resolveCostPrice(Long materialId) {
        try {
            CostBalanceRaw balance = costFlowMapper.selectLatestBalance(materialId);
            if (balance == null || balance.getBalanceQty() == null
                    || balance.getBalanceQty().signum() <= 0 || balance.getBalanceCost() == null) {
                return BigDecimal.ZERO.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            }
            return balance.getBalanceCost().divide(balance.getBalanceQty(), PRICE_SCALE, RoundingMode.HALF_UP);
        } catch (DataAccessException e) {
            // 表不存在等 SQL 层异常：按无成本数据降级（单价 0）
            return BigDecimal.ZERO.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        }
    }

    /**
     * 结算单价：RATE = 成本×(1+加价率%)；FIXED = 成本+固定额；无启用价目 = 成本。
     */
    private BigDecimal applyMarkup(BigDecimal cost, OrgTransferPrice rule) {
        BigDecimal result = cost;
        if (rule != null) {
            if (OrgConstants.MARKUP_RATE.equals(rule.getMarkupType())) {
                BigDecimal markup = cost.multiply(rule.getMarkupValue())
                        .divide(HUNDRED, PRICE_SCALE, RoundingMode.HALF_UP);
                result = cost.add(markup);
            } else if (OrgConstants.MARKUP_FIXED.equals(rule.getMarkupType())) {
                result = cost.add(rule.getMarkupValue());
            }
        }
        return result.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private OrgTransfer requireDraft(Long id) {
        OrgTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw BusinessException.ofKey("erp.org.transfer-not-exists", id);
        }
        if (transfer.getStatus() != OrgConstants.TRANSFER_DRAFT) {
            throw BusinessException.ofKey("erp.org.modify-only-draft", transfer.getStatus());
        }
        return transfer;
    }

    private OrgFactory requireEnabledFactory(Long id, String label) {
        OrgFactory factory = factoryMapper.selectById(id);
        if (factory == null) {
            throw BusinessException.ofKey("erp.org.factory-not-exists-id", label, id);
        }
        if (factory.getStatus() == null || factory.getStatus() != OrgConstants.STATUS_ENABLED) {
            throw BusinessException.ofKey("erp.org.factory-disabled", label, factory.getName());
        }
        return factory;
    }

    private WarehouseRaw requireEnabledWarehouse(Long id, String label) {
        WarehouseRaw warehouse = factoryMapper.selectWarehouse(id);
        if (warehouse == null) {
            throw BusinessException.ofKey("erp.org.warehouse-not-exists-id", label, id);
        }
        if (warehouse.getStatus() == null || warehouse.getStatus() != OrgConstants.STATUS_ENABLED) {
            throw BusinessException.ofKey("erp.org.warehouse-disabled", label, warehouse.getName());
        }
        return warehouse;
    }
}
