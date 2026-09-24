package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 科目借贷发生额聚合原始结果（MyBatis 自动映射，列名下划线转驼峰）。
 * <p>
 * 仅作为 {@code FinQueryMapper} 的 resultType 使用，业务层不直接出参。
 *
 * @param openingDebitSum  期初借方累计（from 之前）
 * @param openingCreditSum 期初贷方累计
 * @param periodDebitSum   本期借方合计 [from, to]
 * @param periodCreditSum  本期贷方合计
 */
public record SubjectAggregateRawVO(BigDecimal openingDebitSum, BigDecimal openingCreditSum,
                                    BigDecimal periodDebitSum, BigDecimal periodCreditSum) {
}
