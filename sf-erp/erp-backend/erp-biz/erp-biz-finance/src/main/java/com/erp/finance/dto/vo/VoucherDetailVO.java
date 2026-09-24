package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 凭证明细 VO（主表信息 + 分录行）。
 *
 * @param id              凭证 ID
 * @param voucherNo       凭证号
 * @param voucherDate     凭证日期
 * @param word            凭证字
 * @param attachmentCount 附件张数
 * @param totalDebit      借方合计
 * @param totalCredit     贷方合计
 * @param status          状态：DRAFT / POSTED
 * @param sourceType      业务来源类型（V521 模板引擎；NULL=手工凭证）
 * @param sourceId        业务来源单据 ID
 * @param remark          备注
 * @param createTime      创建时间
 * @param entries         分录行（按行号升序）
 */
public record VoucherDetailVO(Long id, String voucherNo, LocalDate voucherDate, String word,
                              Integer attachmentCount, BigDecimal totalDebit, BigDecimal totalCredit,
                              String status, String sourceType, Long sourceId, String remark,
                              LocalDateTime createTime,
                              List<VoucherEntryVO> entries) {
}
