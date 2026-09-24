package com.erp.sales.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.cost.service.CostService;
import com.erp.framework.security.SecurityUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.service.StockService;
import com.erp.sales.constant.SalesConstants;
import com.erp.sales.dto.form.SalOutstockDetailForm;
import com.erp.sales.dto.form.SalOutstockForm;
import com.erp.sales.dto.vo.SalOutstockDetailBundle;
import com.erp.sales.dto.vo.SalOutstockDetailVO;
import com.erp.sales.dto.vo.SalOutstockVO;
import com.erp.sales.entity.SalOrder;
import com.erp.sales.entity.SalOrderDetail;
import com.erp.sales.entity.SalOutstock;
import com.erp.sales.entity.SalOutstockDetail;
import com.erp.sales.mapper.SalOrderDetailMapper;
import com.erp.sales.mapper.SalOrderMapper;
import com.erp.sales.mapper.SalOutstockDetailMapper;
import com.erp.sales.mapper.SalOutstockMapper;
import com.erp.sales.mapper.SalesBizMapper;
import com.erp.sales.service.SalOutstockService;
import com.erp.sales.service.SalesNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售出库单 Service 实现。
 * <p>
 * 审核（三模块集成接线后）事务步骤（顺序重要，任一步失败整体回滚）：
 * <ol>
 *   <li>条件更新单据状态 0→10（防并发重复审核，audit_by=当前登录用户）；</li>
 *   <li>逐行经 {@link StockService#stockOut} 策略出库（批次物料 FIFO 按批次行拆减，
 *       跨批自动拆多行流水；非批次物料走汇总行原逻辑；库存不足抛异常整体回滚）；</li>
 *   <li>逐行调 {@link CostService#onStockOut} 实时计价（SAL_OUT 按移动加权价），
 *       并把返回的实际出库成本回写 sal_outstock_detail.actual_cost（销售毛利升级依据）；</li>
 *   <li>生成 fin_receivable 应收（金额=单据合计，未结算）；</li>
 *   <li>回写订单行 out_qty（条件防超发）并刷新订单状态（部分出库/已完成）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalOutstockServiceImpl implements SalOutstockService {

    /** 成本模块约定的业务类型编码（与 inventory InvBizType.SAL_OUT 一致） */
    private static final String COST_BIZ_SAL_OUT = "SAL_OUT";

    private final SalOrderMapper orderMapper;
    private final SalOrderDetailMapper orderDetailMapper;
    private final SalOutstockMapper outstockMapper;
    private final SalOutstockDetailMapper outstockDetailMapper;
    private final SalesBizMapper bizMapper;
    private final SalesNoGenerator noGenerator;
    /** 库存唯一入口（批次感知 FIFO，替代一期裸 SQL 操作 inv_stock/inv_movement） */
    private final StockService stockService;
    /** 计价唯一入口（同事务实时归集销售出库成本） */
    private final CostService costService;

    @Override
    public PageResult<SalOutstockVO> page(long pageNum, long pageSize, String keyword, Integer status) {
        Page<SalOutstockVO> page = bizMapper.selectOutstockPage(new Page<>(pageNum, pageSize), keyword, status);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SalOutstockDetailBundle getDetail(Long id) {
        SalOutstockVO outstock = requireOutstockVo(id);
        List<SalOutstockDetailVO> details = bizMapper.selectOutstockDetails(id);
        return new SalOutstockDetailBundle(outstock, details);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalOutstockForm form) {
        SalOrder order = orderMapper.selectById(form.getOrderId());
        if (order == null) {
            throw BusinessException.ofKey("erp.sal.order-not-exists", form.getOrderId());
        }
        if (order.getStatus() != SalesConstants.ORDER_AUDITED
                && order.getStatus() != SalesConstants.ORDER_PART_OUT) {
            throw BusinessException.ofKey("erp.sal.order-status-no-outbound", order.getOrderNo());
        }

        // 订单行快照：选单行必须归属该订单，且数量不得超过剩余可出数量
        Map<Long, SalOrderDetail> orderLines = loadOrderLines(order.getId());
        List<SalOutstockDetail> details = new ArrayList<>(form.getDetails().size());
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalOutstockDetailForm d : form.getDetails()) {
            SalOrderDetail line = orderLines.get(d.getOrderDetailId());
            if (line == null) {
                throw BusinessException.ofKey("erp.sal.order-line-not-in-order", d.getOrderDetailId(), order.getOrderNo());
            }
            BigDecimal remaining = line.getQty().subtract(line.getOutQty());
            if (d.getQty().compareTo(remaining) > 0) {
                throw BusinessException.ofKey("erp.sal.outbound-over-remaining", line.getMaterialId(), d.getQty().toPlainString(), remaining.toPlainString());
            }
            SalOutstockDetail detail = new SalOutstockDetail();
            detail.setOrderDetailId(line.getId());
            detail.setMaterialId(line.getMaterialId());
            detail.setQty(d.getQty());
            detail.setPrice(line.getPrice());
            BigDecimal amount = d.getQty().multiply(line.getPrice()).setScale(2, RoundingMode.HALF_UP);
            detail.setAmount(amount);
            details.add(detail);
            totalQty = totalQty.add(d.getQty());
            totalAmount = totalAmount.add(amount);
        }

        SalOutstock outstock = new SalOutstock();
        outstock.setOutNo(noGenerator.next(SalesConstants.NO_PREFIX_OUTSTOCK,
                () -> outstockMapper.selectCount(Wrappers.lambdaQuery(SalOutstock.class)
                        .likeRight(SalOutstock::getOutNo, todayPrefix()))));
        outstock.setOrderId(order.getId());
        outstock.setOrderNo(order.getOrderNo());
        outstock.setCustomerId(order.getCustomerId());
        outstock.setWarehouseId(order.getWarehouseId());
        outstock.setOutDate(form.getOutDate());
        outstock.setTotalQty(totalQty.setScale(4, RoundingMode.HALF_UP));
        outstock.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        outstock.setStatus(SalesConstants.BIZ_DRAFT);
        outstock.setRemark(form.getRemark());
        outstockMapper.insert(outstock);

        for (SalOutstockDetail detail : details) {
            detail.setOutId(outstock.getId());
            outstockDetailMapper.insert(detail);
        }
        return outstock.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SalOutstock outstock = requireOutstock(id);
        if (outstock.getStatus() != SalesConstants.BIZ_DRAFT) {
            throw BusinessException.ofKey("erp.sal.outstock-only-draft-audit");
        }
        // 审计日志：出库审核联动扣库存+成本计价+生应收，完成行需单号/数量/金额可检索
        log.info("[销售出库][审核] 开始 单号={} 订单={} 仓库id={} 操作人={}",
                outstock.getOutNo(), outstock.getOrderNo(), outstock.getWarehouseId(),
                SecurityUtils.getCurrentUserId());

        // 1. 状态条件流转（0→10），防并发重复审核
        int rows = bizMapper.auditOutstock(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            log.warn("[销售出库][审核] 拒绝 单号={} 原因=状态已变更（并发重复审核）", outstock.getOutNo());
            throw BusinessException.ofKey("erp.sal.outstock-status-changed-audit-failed");
        }

        List<SalOutstockDetail> details = outstockDetailMapper.selectList(
                Wrappers.lambdaQuery(SalOutstockDetail.class).eq(SalOutstockDetail::getOutId, id));

        // 2. StockService 策略出库（批次物料 FIFO 跨批拆行流水；非批次走汇总行；
        //    库存不足抛异常整体回滚，warn 由 StockService 落）+ 3. CostService 实时计价并回写行实际成本
        BigDecimal totalOutCost = BigDecimal.ZERO;
        for (SalOutstockDetail d : details) {
            stockService.stockOut(outstock.getWarehouseId(), d.getMaterialId(), d.getQty(),
                    OutStrategy.FIFO, null, InvBizType.SAL_OUT, id, outstock.getOutNo(),
                    "销售出库 " + outstock.getOutNo());
            // 出库成本按当前移动加权单价计价（返回值即本次实际成本）
            BigDecimal outCost = costService.onStockOut(d.getMaterialId(), outstock.getWarehouseId(),
                    COST_BIZ_SAL_OUT, outstock.getOutNo(), id, d.getQty(), outstock.getOutDate());
            totalOutCost = totalOutCost.add(outCost);
            // 回写明细实际成本（actual_cost 为 NULL 的历史行，统计回退 ref_cost）
            SalOutstockDetail costUpd = new SalOutstockDetail();
            costUpd.setId(d.getId());
            costUpd.setActualCost(outCost);
            outstockDetailMapper.updateById(costUpd);
        }

        // 4. 生成应收（蓝字，未核销；状态值与 fin 模块 V501 约定一致）
        bizMapper.insertReceivable(outstock.getCustomerId(), outstock.getOutNo(),
                outstock.getOutDate(), outstock.getTotalAmount(),
                SalesConstants.RECEIVABLE_UNSETTLED, "销售出库 " + outstock.getOutNo());

        // 5. 回写订单行出库数量（条件更新防并发超发），再刷新订单状态
        for (SalOutstockDetail d : details) {
            int updated = bizMapper.addOrderOutQty(d.getOrderDetailId(), d.getQty());
            if (updated == 0) {
                // warn：超发被守卫拦截（并发出库占用剩余量），订单数量对不上的排查入口
                log.warn("[销售出库][审核] 拒绝 单号={} 订单行id={} 原因=剩余数量不足，事务回滚",
                        outstock.getOutNo(), d.getOrderDetailId());
                throw BusinessException.ofKey("erp.sal.order-line-remaining-insufficient", d.getOrderDetailId());
            }
        }
        bizMapper.refreshOrderFinished(outstock.getOrderId());
        bizMapper.refreshOrderPartOut(outstock.getOrderId());
        log.info("[销售出库][审核] 完成 单号={} 明细数={} 总数量={} 应收金额={} 实际出库成本={} 操作人={}",
                outstock.getOutNo(), details.size(),
                outstock.getTotalQty().stripTrailingZeros().toPlainString(),
                outstock.getTotalAmount().toPlainString(),
                totalOutCost.toPlainString(), SecurityUtils.getCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalOutstock outstock = requireOutstock(id);
        if (outstock.getStatus() != SalesConstants.BIZ_DRAFT) {
            throw BusinessException.ofKey("erp.sal.outstock-only-draft-delete");
        }
        outstockMapper.deleteById(id);
        outstockDetailMapper.delete(Wrappers.lambdaQuery(SalOutstockDetail.class)
                .eq(SalOutstockDetail::getOutId, id));
    }

    // ==================== 私有方法 ====================

    /** 加载订单行并按行 ID 建索引（同订单行重复提交时以数量校验兜底）。 */
    private Map<Long, SalOrderDetail> loadOrderLines(Long orderId) {
        List<SalOrderDetail> lines = orderDetailMapper.selectList(
                Wrappers.lambdaQuery(SalOrderDetail.class).eq(SalOrderDetail::getOrderId, orderId));
        Map<Long, SalOrderDetail> index = new HashMap<>(lines.size());
        for (SalOrderDetail line : lines) {
            index.put(line.getId(), line);
        }
        return index;
    }

    private SalOutstock requireOutstock(Long id) {
        SalOutstock outstock = outstockMapper.selectById(id);
        if (outstock == null) {
            throw BusinessException.ofKey("erp.sal.outstock-not-exists-id", id);
        }
        return outstock;
    }

    private SalOutstockVO requireOutstockVo(Long id) {
        SalOutstockVO vo = bizMapper.selectOutstock(id);
        if (vo == null) {
            throw BusinessException.ofKey("erp.sal.outstock-not-exists-id", id);
        }
        return vo;
    }

    /** 今日单号前缀（降级计数用），如 XS20260905。 */
    private String todayPrefix() {
        return SalesConstants.NO_PREFIX_OUTSTOCK
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
