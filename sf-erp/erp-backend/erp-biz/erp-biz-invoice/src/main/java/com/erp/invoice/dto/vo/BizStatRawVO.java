package com.erp.invoice.dto.vo;

import java.math.BigDecimal;

/**
 * 对账业务侧/开票侧聚合原始行。
 *
 * @param cnt       单据数（发票数）
 * @param amountSum 金额合计（不含税口径）
 */
public record BizStatRawVO(Long cnt, BigDecimal amountSum) {
}
