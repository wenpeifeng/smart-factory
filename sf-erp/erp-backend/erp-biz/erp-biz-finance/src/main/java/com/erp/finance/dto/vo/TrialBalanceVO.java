package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试算平衡表 VO（全科目六栏余额表 + 合计行 + 是否平衡标识）。
 * <p>
 * balanced = 期初借贷合计、本期借贷合计、期末借贷合计三组均精确相等
 * （BigDecimal.compareTo）。
 *
 * @param from               期间起（含）
 * @param to                 期间止（含）
 * @param balanced           是否借贷平衡
 * @param totalOpeningDebit  期初借方合计
 * @param totalOpeningCredit 期初贷方合计
 * @param totalPeriodDebit   本期借方合计
 * @param totalPeriodCredit  本期贷方合计
 * @param totalClosingDebit  期末借方合计
 * @param totalClosingCredit 期末贷方合计
 * @param rows               科目行（按编码升序，含无发生额科目）
 */
public record TrialBalanceVO(java.time.LocalDate from, java.time.LocalDate to, boolean balanced,
                             BigDecimal totalOpeningDebit, BigDecimal totalOpeningCredit,
                             BigDecimal totalPeriodDebit, BigDecimal totalPeriodCredit,
                             BigDecimal totalClosingDebit, BigDecimal totalClosingCredit,
                             List<TrialBalanceRowVO> rows) {
}
