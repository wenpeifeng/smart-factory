package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 计价补录结果 VO（POST /cost/hooks/receive）。
 */
public record HookResultVO(
        Long flowId,
        String period,
        /** 补录方向数量（正入负出，与台账一致） */
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal amount,
        /** 补录后物料结存 */
        BigDecimal balanceQty,
        BigDecimal balanceCost) {
}
