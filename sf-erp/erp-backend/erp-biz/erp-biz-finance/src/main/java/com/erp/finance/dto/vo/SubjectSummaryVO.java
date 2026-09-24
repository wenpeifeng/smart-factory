package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 科目汇总 VO（GET /finance/vouchers/summary 出参）。
 * <p>
 * 只统计已过账（POSTED）凭证。余额按科目余额方向取正：
 * 借方科目余额 = 借 - 贷；贷方科目余额 = 贷 - 借。
 *
 * @param subjectId      科目 ID（查询全部科目合计时为 null）
 * @param subjectCode    科目编码
 * @param subjectName    科目名称
 * @param direction      余额方向：DEBIT / CREDIT
 * @param openingBalance 期初余额（按科目方向取正，贷方科目为贷方正数）
 * @param debitTotal     本期借方合计
 * @param creditTotal    本期贷方合计
 * @param closingBalance 期末余额（按科目方向取正）
 */
public record SubjectSummaryVO(Long subjectId, String subjectCode, String subjectName,
                               String direction, BigDecimal openingBalance, BigDecimal debitTotal,
                               BigDecimal creditTotal, BigDecimal closingBalance) {
}
