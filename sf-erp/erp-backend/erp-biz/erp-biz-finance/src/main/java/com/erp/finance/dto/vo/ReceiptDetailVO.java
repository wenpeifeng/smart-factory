package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款单明细 VO（主表信息 + 核销明细行）。
 *
 * @param id             收款单 ID
 * @param receiptNo      收款单号
 * @param customerId     客户 ID
 * @param customerName   客户名称
 * @param accountId      收款账户 ID
 * @param accountName    收款账户名称
 * @param bizDate        收款日期
 * @param amount         收款金额
 * @param verifiedAmount 已核销金额
 * @param remark         备注
 * @param createTime     创建时间
 * @param items          核销明细行
 */
public record ReceiptDetailVO(Long id, String receiptNo, Long customerId, String customerName,
                              Long accountId, String accountName, LocalDate bizDate,
                              BigDecimal amount, BigDecimal verifiedAmount, String remark,
                              LocalDateTime createTime, List<ReceiptItemVO> items) {
}
