package com.erp.cost.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.cost.constant.CostBizType;
import com.erp.cost.constant.CostConstants;
import com.erp.cost.entity.CostFlow;
import com.erp.cost.entity.CostPeriod;
import com.erp.cost.mapper.CostFlowMapper;
import com.erp.cost.mapper.CostPeriodMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 存货计价台账引擎 —— 移动加权平均的记账原语。
 * <p>
 * 抽出为独立组件的原因：实时过账（CostService SPI，采购/销售/生产接线）
 * 与批量重算（cost/sync 按 inv_movement 重放）必须走<strong>完全相同</strong>的
 * 计算路径与舍入规则，才能保证「重算结果与实时计价一致」这一核心校验目标。
 * <p>
 * 并发控制：每次过账先 {@code SELECT ... FOR UPDATE} 锁定该物料最新一行计价流水，
 * 同一物料的过账串行执行；不同物料互不阻塞。
 * <p>
 * 舍入规则（全链统一）：金额 2 位、单价 6 位，HALF_UP。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostLedger {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern(CostConstants.PERIOD_PATTERN);

    private final CostFlowMapper flowMapper;
    private final CostPeriodMapper periodMapper;

    /**
     * 入库过账：新加权价 = (原数量×原单价 + 本次金额)/(原数量+本次数量)，
     * 落一行 qty>0 的流水；amount 为空时按当前加权单价估算（无历史按 0）。
     *
     * @return 已落库的流水（含运行余额）
     */
    public CostFlow postIn(Long materialId, Long warehouseId, CostBizType type, String bizNo,
                           Long bizId, Long movementId, BigDecimal qty, BigDecimal amount, LocalDate bizDate) {
        validateCommon(materialId, type, bizNo);
        if (qty == null || qty.signum() <= 0) {
            throw BusinessException.ofKey("erp.cost.inbound-price-qty-positive", materialId, bizNo);
        }
        String period = periodOf(bizDate);
        assertPeriodOpen(period);

        CostFlow last = lockLast(materialId);
        BigDecimal balQty = last == null ? BigDecimal.ZERO : last.getBalanceQty();
        BigDecimal balCost = last == null ? BigDecimal.ZERO : last.getBalanceCost();

        // 无金额入库（生产入库/其他入库/补账）：按当前加权单价估算
        BigDecimal inAmount = amount != null ? amount.setScale(CostConstants.AMOUNT_SCALE, RoundingMode.HALF_UP)
                : estimateAmount(last, qty);

        BigDecimal unitCost = qty.signum() > 0
                ? inAmount.divide(qty, CostConstants.PRICE_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        CostFlow flow = new CostFlow();
        flow.setBizType(type.getCode());
        flow.setBizId(bizId);
        flow.setBizNo(bizNo);
        flow.setMovementId(movementId);
        flow.setMaterialId(materialId);
        flow.setWarehouseId(warehouseId);
        flow.setBizDate(bizDate);
        flow.setQty(qty);
        flow.setUnitCost(unitCost);
        flow.setAmount(inAmount);
        flow.setBalanceQty(balQty.add(qty));
        flow.setBalanceCost(balCost.add(inAmount));
        flow.setPeriod(period);
        flowMapper.insert(flow);
        // 审计日志：入库计价改变后续所有出库的加权单价，记账依据（数量/金额/新结存）必须留痕
        log.info("[成本][入库过账] 物料={} 业务={} 单号={} 数量={} 金额={} 单价={} 结存数量={} 结存金额={}",
                materialId, type.getCode(), bizNo, qty.stripTrailingZeros().toPlainString(),
                inAmount.toPlainString(), unitCost.toPlainString(),
                flow.getBalanceQty().stripTrailingZeros().toPlainString(),
                flow.getBalanceCost().toPlainString());
        return flow;
    }

    /**
     * 出库过账：按锁定时的移动加权单价计算成本，落一行 qty<0 的流水；
     * 结存数量扣至 0 时结存金额清零（消除连续舍入尾差）。
     *
     * @return 已落库的流水（amount/unit_cost 为本次出库成本，正数列示于列、台账金额列为负）
     */
    public CostFlow postOut(Long materialId, Long warehouseId, CostBizType type, String bizNo,
                            Long bizId, Long movementId, BigDecimal qty, LocalDate bizDate) {
        validateCommon(materialId, type, bizNo);
        if (qty == null || qty.signum() <= 0) {
            throw BusinessException.ofKey("erp.cost.outbound-price-qty-positive", materialId, bizNo);
        }
        String period = periodOf(bizDate);
        assertPeriodOpen(period);

        CostFlow last = lockLast(materialId);
        BigDecimal balQty = last == null ? BigDecimal.ZERO : last.getBalanceQty();
        BigDecimal balCost = last == null ? BigDecimal.ZERO : last.getBalanceCost();
        if (qty.compareTo(balQty) > 0) {
            // warn：计价账面结存不足（库存与台账不一致的信号），需人工核对 inv_stock 与 cost_flow
            log.warn("[成本][出库过账] 拒绝 物料={} 单号={} 原因=出库数量 {} 超过计价账面结存 {}",
                    materialId, bizNo, qty.stripTrailingZeros().toPlainString(),
                    balQty.stripTrailingZeros().toPlainString());
            throw BusinessException.ofKey("erp.cost.outbound-over-balance", materialId, qty.stripTrailingZeros().toPlainString(), balQty.stripTrailingZeros().toPlainString());
        }

        BigDecimal unitCost = weightedUnit(last);
        BigDecimal outAmount = unitCost.multiply(qty)
                .setScale(CostConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal newQty = balQty.subtract(qty);
        // 结存数量为 0 时强制金额清零，避免 0.01 级尾差滞留台账
        BigDecimal newCost = newQty.signum() == 0 ? BigDecimal.ZERO : balCost.subtract(outAmount);

        CostFlow flow = new CostFlow();
        flow.setBizType(type.getCode());
        flow.setBizId(bizId);
        flow.setBizNo(bizNo);
        flow.setMovementId(movementId);
        flow.setMaterialId(materialId);
        flow.setWarehouseId(warehouseId);
        flow.setBizDate(bizDate);
        flow.setQty(qty.negate());
        flow.setUnitCost(unitCost);
        flow.setAmount(outAmount.negate());
        flow.setBalanceQty(newQty);
        flow.setBalanceCost(newCost);
        flow.setPeriod(period);
        flowMapper.insert(flow);
        // 审计日志：出库成本=毛利依据，单价来源（加权）与结存变化必须可追溯
        log.info("[成本][出库过账] 物料={} 业务={} 单号={} 数量={} 出库成本={} 加权单价={} 结存数量={} 结存金额={}",
                materialId, type.getCode(), bizNo, qty.stripTrailingZeros().toPlainString(),
                outAmount.toPlainString(), unitCost.toPlainString(),
                newQty.stripTrailingZeros().toPlainString(), newCost.toPlainString());
        return flow;
    }

    /** 物料最新一行流水（只读，无锁；供纯计算场景） */
    public CostFlow lastRead(Long materialId) {
        return flowMapper.selectOne(Wrappers.lambdaQuery(CostFlow.class)
                .eq(CostFlow::getMaterialId, materialId)
                .orderByDesc(CostFlow::getId)
                .last("LIMIT 1"));
    }

    /** 取物料最新一行流水并加行锁（过账路径统一入口） */
    CostFlow lockLast(Long materialId) {
        return flowMapper.selectLastForUpdate(materialId);
    }

    /** 当前移动加权单价：结存数量>0 用结存金额/数量，否则退化为最后一行单价（无历史 0） */
    BigDecimal weightedUnit(CostFlow last) {
        if (last == null) {
            return BigDecimal.ZERO;
        }
        if (last.getBalanceQty().signum() > 0) {
            return last.getBalanceCost()
                    .divide(last.getBalanceQty(), CostConstants.PRICE_SCALE, RoundingMode.HALF_UP);
        }
        return last.getUnitCost() == null ? BigDecimal.ZERO : last.getUnitCost();
    }

    /** 按当前加权单价估算入库金额（无成本历史按 0 入账） */
    BigDecimal estimateAmount(CostFlow last, BigDecimal qty) {
        return weightedUnit(last).multiply(qty)
                .setScale(CostConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /** 期间归属：业务日期（空=当天）所在月 yyyyMM */
    String periodOf(LocalDate bizDate) {
        return (bizDate == null ? LocalDate.now() : bizDate).format(PERIOD_FMT);
    }

    /** 已结账期间拒绝过账（结账数据不可追溯污染） */
    void assertPeriodOpen(String period) {
        Long closed = periodMapper.selectCount(Wrappers.lambdaQuery(CostPeriod.class)
                .eq(CostPeriod::getPeriod, period)
                .eq(CostPeriod::getStatus, CostConstants.PERIOD_CLOSED));
        if (closed != null && closed > 0) {
            // warn：向已结账期间补录是月末核算红线，拒绝必须留痕（谁在何时试图补录）
            log.warn("[成本][过账] 拒绝 期间={} 原因=期间已结账，禁止再过账计价流水", period);
            throw BusinessException.ofKey("erp.cost.period-closed-no-post", period);
        }
    }

    private void validateCommon(Long materialId, CostBizType type, String bizNo) {
        Objects.requireNonNull(materialId, "materialId");
        if (type == null) {
            throw BusinessException.ofKey("erp.cost.biz-type-required");
        }
        if (bizNo == null || bizNo.isBlank()) {
            throw BusinessException.ofKey("erp.cost.source-doc-no-required", materialId);
        }
    }

}
