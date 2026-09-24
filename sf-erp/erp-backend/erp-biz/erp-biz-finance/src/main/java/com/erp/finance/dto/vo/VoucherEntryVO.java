package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 凭证分录 VO（含科目编码/名称冗余，前端免二次查询）。
 *
 * @param lineNo       行号
 * @param summary      摘要
 * @param subjectId    科目 ID
 * @param subjectCode  科目编码
 * @param subjectName  科目名称
 * @param debitAmount  借方金额
 * @param creditAmount 贷方金额
 */
public record VoucherEntryVO(Integer lineNo, String summary, Long subjectId, String subjectCode,
                             String subjectName, BigDecimal debitAmount, BigDecimal creditAmount) {
}
