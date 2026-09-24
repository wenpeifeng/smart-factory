package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 工单领料成本明细行 VO（料）。
 */
public record IssueCostLineVO(
        Long materialId,
        String materialCode,
        String materialName,
        /** 实领数量 */
        BigDecimal qty,
        /** 移动加权单价 */
        BigDecimal unitCost,
        /** 领料成本 = 数量 × 单价 */
        BigDecimal amount) {
}
