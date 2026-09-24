package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 付款核销明细 VO（应付单关键信息 + 本次核销额）。
 *
 * @param payableId      应付单 ID
 * @param bizNo          应付来源单号
 * @param payableAmount  应付金额
 * @param settledAmount  累计已核销金额
 * @param verifyAmount   本次核销金额
 */
public record PaymentItemVO(Long payableId, String bizNo, BigDecimal payableAmount,
                            BigDecimal settledAmount, BigDecimal verifyAmount) {
}
