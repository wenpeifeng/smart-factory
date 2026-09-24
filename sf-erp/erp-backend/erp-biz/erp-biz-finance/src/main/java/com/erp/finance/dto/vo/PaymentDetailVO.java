package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 付款单明细 VO（主表信息 + 核销明细行，与收款单对称）。
 *
 * @param id             付款单 ID
 * @param paymentNo      付款单号
 * @param supplierId     供应商 ID
 * @param supplierName   供应商名称
 * @param accountId      付款账户 ID
 * @param accountName    付款账户名称
 * @param bizDate        付款日期
 * @param amount         付款金额
 * @param verifiedAmount 已核销金额
 * @param remark         备注
 * @param deptId         费用归属部门（预算控制用，可空）
 * @param subjectId      预算科目（可空）
 * @param createTime     创建时间
 * @param items          核销明细行
 */
public record PaymentDetailVO(Long id, String paymentNo, Long supplierId, String supplierName,
                              Long accountId, String accountName, LocalDate bizDate,
                              BigDecimal amount, BigDecimal verifiedAmount, String remark,
                              Long deptId, Long subjectId,
                              LocalDateTime createTime, List<PaymentItemVO> items) {
}
