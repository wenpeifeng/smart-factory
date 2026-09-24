package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 物料当前移动加权成本余额 VO（CostService 查询出参）。
 */
public record MaterialBalanceVO(
        Long materialId,
        /** 当前结存数量（全仓合计） */
        BigDecimal qty,
        /** 当前结存金额 */
        BigDecimal amount,
        /** 当前移动加权单价（qty=0 时为最后一次入库单价，无历史为 0） */
        BigDecimal unitCost) {
}
