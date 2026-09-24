package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 成本趋势点 VO（按期间）。
 */
public record TrendPointVO(
        String period,
        /** 本期入库金额 */
        BigDecimal inAmount,
        /** 本期出库成本合计 */
        BigDecimal outAmount,
        /** 主营业务成本（SAL_OUT 口径） */
        BigDecimal salOutCost,
        /** 生产领料成本（PROD_USE 口径） */
        BigDecimal prodUseCost,
        /** 期末存货余额（累计推算） */
        BigDecimal stockBalance) {
}
