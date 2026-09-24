package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 试算平衡表行 VO（经典六栏：期初借/贷、本期借/贷、期末借/贷）。
 * <p>
 * 余额栏按借贷净额方向列示：净借方余额落"借"栏，净贷方余额落"贷"栏。
 *
 * @param subjectId    科目 ID
 * @param code         科目编码
 * @param name         科目名称
 * @param category     科目类别
 * @param direction    余额方向
 * @param openingDebit  期初余额（借方）
 * @param openingCredit 期初余额（贷方）
 * @param periodDebit   本期发生（借方）
 * @param periodCredit  本期发生（贷方）
 * @param closingDebit  期末余额（借方）
 * @param closingCredit 期末余额（贷方）
 */
public record TrialBalanceRowVO(Long subjectId, String code, String name, String category,
                                String direction, BigDecimal openingDebit, BigDecimal openingCredit,
                                BigDecimal periodDebit, BigDecimal periodCredit,
                                BigDecimal closingDebit, BigDecimal closingCredit) {
}
