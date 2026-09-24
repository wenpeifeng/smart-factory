package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinReceipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收款单 VO。
 *
 * @param id             收款单 ID
 * @param receiptNo      收款单号
 * @param customerId     客户 ID
 * @param customerName   客户名称（可能为 null）
 * @param accountId      收款账户 ID
 * @param accountName    收款账户名称
 * @param bizDate        收款日期
 * @param amount         收款金额
 * @param verifiedAmount 已核销金额
 * @param remark         备注
 * @param createTime     创建时间
 */
public record ReceiptVO(Long id, String receiptNo, Long customerId, String customerName,
                        Long accountId, String accountName, LocalDate bizDate, BigDecimal amount,
                        BigDecimal verifiedAmount, String remark, LocalDateTime createTime) {

    /** 实体转 VO（名称由 Service 层回填） */
    public static ReceiptVO from(FinReceipt r, String customerName, String accountName) {
        return new ReceiptVO(r.getId(), r.getReceiptNo(), r.getCustomerId(), customerName,
                r.getAccountId(), accountName, r.getBizDate(), r.getAmount(),
                r.getVerifiedAmount(), r.getRemark(), r.getCreateTime());
    }
}
