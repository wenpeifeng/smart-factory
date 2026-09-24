package com.erp.invoice.dto.vo;

import java.math.BigDecimal;

/**
 * 税额聚合原始行（SUM 结果包装，空集由 SQL COALESCE 兜底为 0）。
 *
 * @param amountSum 不含税金额合计
 * @param taxSum    税额合计
 */
public record TaxStatRawVO(BigDecimal amountSum, BigDecimal taxSum) {
}
