package com.erp.invoice.dto.request;

import java.time.LocalDate;

/**
 * 进项发票分页查询条件。
 *
 * @param supplierId 供应商 ID（精确）
 * @param status     状态：0待认证 10已认证 20已红冲（精确）
 * @param keyword    发票号码 / 业务单号模糊查询
 * @param from       开票日期起（含，invoice_date 区间）
 * @param to         开票日期止（含）
 */
public record PurchaseInvoiceQuery(Long supplierId, Integer status, String keyword, LocalDate from, LocalDate to) {
}
