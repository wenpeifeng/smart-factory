package com.erp.invoice.dto.request;

import java.time.LocalDate;

/**
 * 销项发票分页查询条件。
 *
 * @param customerId 客户 ID（精确）
 * @param status     状态：0待开票 10已开票 20已红冲（精确）
 * @param keyword    发票号码 / 业务单号模糊查询
 * @param from       开票日期起（含，invoice_date 区间）
 * @param to         开票日期止（含）
 */
public record SalesInvoiceQuery(Long customerId, Integer status, String keyword, LocalDate from, LocalDate to) {
}
