package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款单 VO（与收款单对称）。
 *
 * @param id             付款单 ID
 * @param paymentNo      付款单号
 * @param supplierId     供应商 ID
 * @param supplierName   供应商名称（可能为 null）
 * @param accountId      付款账户 ID
 * @param accountName    付款账户名称
 * @param bizDate        付款日期
 * @param amount         付款金额
 * @param verifiedAmount 已核销金额
 * @param remark         备注
 * @param deptId         费用归属部门（预算控制用，可空）
 * @param subjectId      预算科目（可空）
 * @param createTime     创建时间
 */
public record PaymentVO(Long id, String paymentNo, Long supplierId, String supplierName,
                        Long accountId, String accountName, LocalDate bizDate, BigDecimal amount,
                        BigDecimal verifiedAmount, String remark, Long deptId, Long subjectId,
                        LocalDateTime createTime) {

    /** 实体转 VO（名称由 Service 层回填） */
    public static PaymentVO from(FinPayment p, String supplierName, String accountName) {
        return new PaymentVO(p.getId(), p.getPaymentNo(), p.getSupplierId(), supplierName,
                p.getAccountId(), accountName, p.getBizDate(), p.getAmount(),
                p.getVerifiedAmount(), p.getRemark(), p.getDeptId(), p.getSubjectId(),
                p.getCreateTime());
    }
}
