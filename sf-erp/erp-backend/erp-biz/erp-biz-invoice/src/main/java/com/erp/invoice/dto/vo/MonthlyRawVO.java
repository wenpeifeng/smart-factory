package com.erp.invoice.dto.vo;

import java.math.BigDecimal;

/**
 * 月度价税聚合原始行（按月分组的 SUM，月份 1-12）。
 *
 * @param month    月份（1-12）
 * @param totalSum 价税合计
 * @param taxSum   税额
 */
public record MonthlyRawVO(Integer month, BigDecimal totalSum, BigDecimal taxSum) {
}
