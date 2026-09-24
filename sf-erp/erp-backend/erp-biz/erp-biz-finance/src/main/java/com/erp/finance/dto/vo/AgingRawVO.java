package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应收/应付账龄原始行（MyBatis 自动映射，应收 partnerId=customer_id、应付=supplier_id）。
 *
 * @param id            单据 ID
 * @param partnerId     客商 ID（客户/供应商）
 * @param bizNo         业务单号
 * @param bizDate       单据日期（账龄锚点首选，可空）
 * @param dueDate       到期日（可空，当前账龄口径不使用，仅透出便于核对）
 * @param createTime    创建时间（单据日期缺失时的回退锚点）
 * @param amount        单据金额
 * @param settledAmount 已核销金额
 */
public record AgingRawVO(Long id, Long partnerId, String bizNo, LocalDate bizDate,
                         LocalDate dueDate, LocalDateTime createTime,
                         BigDecimal amount, BigDecimal settledAmount) {
}
