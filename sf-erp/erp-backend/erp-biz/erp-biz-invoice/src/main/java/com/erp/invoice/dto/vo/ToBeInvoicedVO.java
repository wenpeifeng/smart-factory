package com.erp.invoice.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户待开票清单行（GET /invoice/sales/toBeInvoiced）。
 * <p>
 * 口径：已审核（status=10）销售出库单 − 已存在未红冲蓝字发票（待开票/已开票）的出库单；
 * 金额取出库明细汇总（明细缺失时回退单头 total_amount）。
 *
 * @param outstockId   出库单 ID
 * @param outNo        出库单号
 * @param customerId   客户 ID
 * @param customerName 客户名称（可能为 null）
 * @param outDate      出库日期
 * @param amount       不含税金额（明细汇总）
 */
public record ToBeInvoicedVO(Long outstockId, String outNo, Long customerId, String customerName,
                             LocalDate outDate, BigDecimal amount) {
}
