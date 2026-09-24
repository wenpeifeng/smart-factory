package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.i18n.MessageKeys;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.cost.service.CostService;
import com.erp.framework.mybatis.PageUtils;
import com.erp.framework.security.SecurityUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.service.StockService;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.IssueForm;
import com.erp.mrp.dto.form.IssueItemForm;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.MaterialIssueItemVO;
import com.erp.mrp.dto.vo.MaterialIssueVO;
import com.erp.mrp.entity.MrpBomItem;
import com.erp.mrp.entity.MrpMaterialIssue;
import com.erp.mrp.entity.MrpMaterialIssueItem;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.mapper.MrpBomItemMapper;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import com.erp.mrp.mapper.MrpMaterialIssueItemMapper;
import com.erp.mrp.mapper.MrpMaterialIssueMapper;
import com.erp.mrp.mapper.MrpWorkOrderMapper;
import com.erp.mrp.service.MrpMaterialIssueService;
import com.erp.mrp.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 生产领料服务实现。
 * <p>
 * 应领数量 = BOM用量 × 订单数量 × (1 + 损耗率/100)；
 * 累计实领（含本单）不允许超过应领总量（跨领料单累计校验）；
 * 审核（三模块集成接线后）：每子件经 {@link StockService#stockOut} 策略出库
 * （批次物料 FIFO 按批次行拆减——修复一期裸 SQL 对批次行多行命中的缺陷；
 * 非批次物料走汇总行原逻辑，库存不足整单回滚）+ 同事务调
 * {@link CostService#onStockOut} 实时归集领料成本（PROD_USE，月结转生产成本）。
 */
@Service
@RequiredArgsConstructor
public class MrpMaterialIssueServiceImpl implements MrpMaterialIssueService {

    /** 成本模块约定的业务类型编码（与 inventory InvBizType.PROD_USE 一致） */
    private static final String COST_BIZ_PROD_USE = "PROD_USE";

    private final MrpMaterialIssueMapper issueMapper;
    private final MrpMaterialIssueItemMapper issueItemMapper;
    private final MrpWorkOrderMapper workOrderMapper;
    private final MrpBomItemMapper bomItemMapper;
    private final MrpIntegrationMapper integrationMapper;
    private final MrpLookupHelper lookup;
    private final SeqNoService seqNoService;
    /** 库存唯一入口（批次感知 FIFO，替代一期裸 SQL 操作 inv_stock/inv_movement） */
    private final StockService stockService;
    /** 计价唯一入口（同事务实时归集领料成本） */
    private final CostService costService;

    @Override
    public PageResult<MaterialIssueVO> page(long pageNum, long pageSize, Long workOrderId, String status) {
        PageResult<MrpMaterialIssue> page = PageUtils.of(issueMapper,
                Wrappers.lambdaQuery(MrpMaterialIssue.class)
                        .eq(workOrderId != null, MrpMaterialIssue::getWorkOrderId, workOrderId)
                        .eq(status != null && !status.isBlank(), MrpMaterialIssue::getStatus, status)
                        .orderByDesc(MrpMaterialIssue::getId),
                pageNum, pageSize);
        // 批量取明细回显（一次查询全部相关单据的明细）
        List<Long> issueIds = page.getRecords().stream().map(MrpMaterialIssue::getId).toList();
        Map<Long, List<MrpMaterialIssueItem>> itemMap = listItemMap(issueIds);
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                itemMap.values().stream().flatMap(List::stream)
                        .map(MrpMaterialIssueItem::getMaterialId).toList());
        return page.map(issue -> toVO(issue, itemMap.getOrDefault(issue.getId(), List.of()), materials));
    }

    @Override
    public MaterialIssueVO detail(Long id) {
        MrpMaterialIssue issue = requireIssue(id);
        List<MrpMaterialIssueItem> items = listItems(id);
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                items.stream().map(MrpMaterialIssueItem::getMaterialId).toList());
        return toVO(issue, items, materials);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(IssueForm form) {
        MrpWorkOrder wo = requireReleasableOrder(form.getWorkOrderId());
        Map<Long, BigDecimal> shouldMap = buildShouldQty(wo);
        Map<Long, BigDecimal> actualMap = resolveActual(wo, form, shouldMap);
        validateCumulative(wo.getId(), form.getWarehouseId(), shouldMap, actualMap, null);

        MrpMaterialIssue issue = new MrpMaterialIssue();
        issue.setIssueNo(seqNoService.next(MrpConstants.PREFIX_ISSUE));
        issue.setWorkOrderId(wo.getId());
        issue.setWarehouseId(form.getWarehouseId());
        issue.setStatus(MrpConstants.ISSUE_DRAFT);
        issue.setRemark(form.getRemark());
        issueMapper.insert(issue);
        insertItems(issue.getId(), shouldMap, actualMap);
        return issue.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, IssueForm form) {
        MrpMaterialIssue issue = requireIssue(id);
        if (!MrpConstants.ISSUE_DRAFT.equals(issue.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.picking-only-draft-modify");
        }
        MrpWorkOrder wo = requireReleasableOrder(form.getWorkOrderId());
        Map<Long, BigDecimal> shouldMap = buildShouldQty(wo);
        Map<Long, BigDecimal> actualMap = resolveActual(wo, form, shouldMap);
        validateCumulative(wo.getId(), form.getWarehouseId(), shouldMap, actualMap, id);

        MrpMaterialIssue upd = new MrpMaterialIssue();
        upd.setId(id);
        upd.setWorkOrderId(wo.getId());
        upd.setWarehouseId(form.getWarehouseId());
        upd.setRemark(form.getRemark());
        issueMapper.updateById(upd);
        issueItemMapper.delete(Wrappers.lambdaQuery(MrpMaterialIssueItem.class)
                .eq(MrpMaterialIssueItem::getIssueId, id));
        insertItems(id, shouldMap, actualMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        MrpMaterialIssue issue = requireIssue(id);
        if (!MrpConstants.ISSUE_DRAFT.equals(issue.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.picking-only-draft-audit");
        }
        MrpWorkOrder wo = workOrderMapper.selectById(issue.getWorkOrderId());
        if (wo == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.picking-order-not-exists");
        }
        // 逐子件：StockService 策略出库（批次物料 FIFO 跨批拆行；非批次汇总行；
        // 库存不足由 StockService 抛异常整单回滚）+ CostService 实时归集领料成本
        for (MrpMaterialIssueItem item : listItems(id)) {
            if (item.getActualQty().signum() <= 0) {
                continue;
            }
            stockService.stockOut(issue.getWarehouseId(), item.getMaterialId(), item.getActualQty(),
                    OutStrategy.FIFO, null, InvBizType.PROD_USE, id, issue.getIssueNo(),
                    "生产领料 " + issue.getIssueNo());
            costService.onStockOut(item.getMaterialId(), issue.getWarehouseId(), COST_BIZ_PROD_USE,
                    issue.getIssueNo(), id, item.getActualQty(), null);
        }
        MrpMaterialIssue upd = new MrpMaterialIssue();
        upd.setId(id);
        upd.setStatus(MrpConstants.ISSUE_AUDITED);
        issueMapper.updateById(upd);
        // 订单首次领料 → 生产中
        if (MrpConstants.WO_RELEASED.equals(wo.getStatus())) {
            MrpWorkOrder woUpd = new MrpWorkOrder();
            woUpd.setId(wo.getId());
            woUpd.setStatus(MrpConstants.WO_PRODUCING);
            workOrderMapper.updateById(woUpd);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MrpMaterialIssue issue = requireIssue(id);
        if (!MrpConstants.ISSUE_DRAFT.equals(issue.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.picking-only-draft-delete");
        }
        issueMapper.deleteById(id);
        issueItemMapper.delete(Wrappers.lambdaQuery(MrpMaterialIssueItem.class)
                .eq(MrpMaterialIssueItem::getIssueId, id));
    }

    // ==================== 私有工具 ====================

    /** 订单必须处于已下达/生产中才可领料 */
    private MrpWorkOrder requireReleasableOrder(Long workOrderId) {
        MrpWorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.production-order-not-exists", workOrderId);
        }
        if (!MrpConstants.WO_RELEASED.equals(wo.getStatus())
                && !MrpConstants.WO_PRODUCING.equals(wo.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.picking-status-not-allowed", wo.getStatus());
        }
        if (MrpConstants.PICK_BACKFLUSH.equals(wo.getPickType())) {
            throw BusinessException.ofKey(MessageKeys.MRP_BACKFLUSH_NO_MANUAL_ISSUE);
        }
        return wo;
    }

    /** 应领数量：BOM用量 × 订单数量 × (1+损耗率/100)，保留6位小数 */
    private Map<Long, BigDecimal> buildShouldQty(MrpWorkOrder wo) {
        List<MrpBomItem> bomItems = bomItemMapper.selectList(
                Wrappers.lambdaQuery(MrpBomItem.class).eq(MrpBomItem::getBomId, wo.getBomId()));
        if (bomItems.isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.bom-no-components-no-picking", wo.getBomId());
        }
        Map<Long, BigDecimal> shouldMap = new LinkedHashMap<>();
        for (MrpBomItem item : bomItems) {
            BigDecimal lossRate = item.getLossRate() == null ? BigDecimal.ZERO : item.getLossRate();
            BigDecimal should = item.getQty().multiply(wo.getQty())
                    .multiply(BigDecimal.ONE.add(lossRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(6, RoundingMode.HALF_UP);
            shouldMap.merge(item.getMaterialId(), should, BigDecimal::add);
        }
        return shouldMap;
    }

    /** 实领数量：未传明细=按应领全量；传了则校验物料必须在 BOM 内 */
    private Map<Long, BigDecimal> resolveActual(MrpWorkOrder wo, IssueForm form,
                                                Map<Long, BigDecimal> shouldMap) {
        Map<Long, BigDecimal> actualMap = new LinkedHashMap<>();
        if (form.getItems() == null || form.getItems().isEmpty()) {
            shouldMap.forEach((materialId, should) -> actualMap.put(materialId, should));
            return actualMap;
        }
        for (IssueItemForm itemForm : form.getItems()) {
            if (!shouldMap.containsKey(itemForm.getMaterialId())) {
                throw BusinessException.ofKey("erp.mrp.material-not-in-bom", itemForm.getMaterialId());
            }
            if (itemForm.getActualQty().compareTo(shouldMap.get(itemForm.getMaterialId())) > 0) {
                throw BusinessException.ofKey("erp.mrp.picked-over-required", itemForm.getMaterialId());
            }
            actualMap.put(itemForm.getMaterialId(), itemForm.getActualQty());
        }
        return actualMap;
    }

    /**
     * 累计超领校验：该订单已审核领料单的实领合计 + 本单实领 ≤ 应领总量。
     *
     * @param excludeIssueId 修改场景排除自身
     */
    private void validateCumulative(Long workOrderId, Long warehouseId,
                                    Map<Long, BigDecimal> shouldMap,
                                    Map<Long, BigDecimal> actualMap, Long excludeIssueId) {
        // 已审核的领料单ID集合
        List<Long> auditedIds = issueMapper.selectList(Wrappers.lambdaQuery(MrpMaterialIssue.class)
                        .eq(MrpMaterialIssue::getWorkOrderId, workOrderId)
                        .eq(MrpMaterialIssue::getStatus, MrpConstants.ISSUE_AUDITED))
                .stream().map(MrpMaterialIssue::getId).toList();
        Map<Long, BigDecimal> auditedActual = new HashMap<>();
        if (!auditedIds.isEmpty()) {
            for (MrpMaterialIssueItem item : issueItemMapper.selectList(
                    Wrappers.lambdaQuery(MrpMaterialIssueItem.class)
                            .in(MrpMaterialIssueItem::getIssueId, auditedIds)
                            .ne(excludeIssueId != null, MrpMaterialIssueItem::getIssueId, excludeIssueId))) {
                auditedActual.merge(item.getMaterialId(), item.getActualQty(), BigDecimal::add);
            }
        }
        for (var entry : actualMap.entrySet()) {
            BigDecimal total = auditedActual.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .add(entry.getValue());
            if (total.compareTo(shouldMap.get(entry.getKey())) > 0) {
                throw BusinessException.ofKey("erp.mrp.cumulative-picking-over", entry.getKey(), total.stripTrailingZeros().toPlainString(), shouldMap.get(entry.getKey()).stripTrailingZeros().toPlainString());
            }
        }
    }

    private void insertItems(Long issueId, Map<Long, BigDecimal> shouldMap,
                             Map<Long, BigDecimal> actualMap) {
        for (var entry : actualMap.entrySet()) {
            MrpMaterialIssueItem item = new MrpMaterialIssueItem();
            item.setIssueId(issueId);
            item.setMaterialId(entry.getKey());
            item.setShouldQty(shouldMap.get(entry.getKey()));
            item.setActualQty(entry.getValue());
            issueItemMapper.insert(item);
        }
    }

    private MrpMaterialIssue requireIssue(Long id) {
        MrpMaterialIssue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.picking-not-exists", id);
        }
        return issue;
    }

    private List<MrpMaterialIssueItem> listItems(Long issueId) {
        return issueItemMapper.selectList(Wrappers.lambdaQuery(MrpMaterialIssueItem.class)
                .eq(MrpMaterialIssueItem::getIssueId, issueId)
                .orderByAsc(MrpMaterialIssueItem::getId));
    }

    private Map<Long, List<MrpMaterialIssueItem>> listItemMap(List<Long> issueIds) {
        if (issueIds.isEmpty()) {
            return Map.of();
        }
        return issueItemMapper.selectList(Wrappers.lambdaQuery(MrpMaterialIssueItem.class)
                        .in(MrpMaterialIssueItem::getIssueId, issueIds))
                .stream().collect(Collectors.groupingBy(MrpMaterialIssueItem::getIssueId));
    }

    private MaterialIssueVO toVO(MrpMaterialIssue issue, List<MrpMaterialIssueItem> items,
                                 Map<Long, MaterialBriefVO> materials) {
        MaterialIssueVO vo = new MaterialIssueVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setWorkOrderId(issue.getWorkOrderId());
        vo.setWarehouseId(issue.getWarehouseId());
        vo.setIssueType(issue.getIssueType());
        vo.setStatus(issue.getStatus());
        vo.setRemark(issue.getRemark());
        vo.setCreateTime(issue.getCreateTime());
        vo.setItems(items.stream().map(item -> {
            MaterialIssueItemVO itemVO = new MaterialIssueItemVO();
            itemVO.setId(item.getId());
            itemVO.setMaterialId(item.getMaterialId());
            MaterialBriefVO m = materials.get(item.getMaterialId());
            if (m != null) {
                itemVO.setMaterialCode(m.getCode());
                itemVO.setMaterialName(m.getName());
                itemVO.setUnit(m.getUnit());
            }
            itemVO.setShouldQty(item.getShouldQty());
            itemVO.setActualQty(item.getActualQty());
            return itemVO;
        }).collect(Collectors.toCollection(ArrayList::new)));
        return vo;
    }
}
