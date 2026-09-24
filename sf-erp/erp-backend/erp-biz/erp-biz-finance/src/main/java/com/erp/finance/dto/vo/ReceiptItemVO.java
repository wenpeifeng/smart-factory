package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 收款核销明细 VO（应收单关键信息 + 本次核销额）。
 *
 * @param receivableId     应收单 ID
 * @param bizNo            应收来源单号
 * @param receivableAmount 应收金额
 * @param settledAmount    累计已核销金额
 * @param verifyAmount     本次核销金额
 */
public record ReceiptItemVO(Long receivableId, String bizNo, BigDecimal receivableAmount,
                            BigDecimal settledAmount, BigDecimal verifyAmount) {
}
