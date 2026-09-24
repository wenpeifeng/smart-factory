package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 试算平衡表原始行（MyBatis 自动映射）。
 *
 * @param subjectId        科目 ID
 * @param code             科目编码
 * @param name             科目名称
 * @param category         科目类别
 * @param direction        余额方向
 * @param openingDebitSum  期初借方累计
 * @param openingCreditSum 期初贷方累计
 * @param periodDebitSum   本期借方合计
 * @param periodCreditSum  本期贷方合计
 */
public record TrialBalanceRawVO(Long subjectId, String code, String name, String category,
                                String direction, BigDecimal openingDebitSum,
                                BigDecimal openingCreditSum, BigDecimal periodDebitSum,
                                BigDecimal periodCreditSum) {
}
