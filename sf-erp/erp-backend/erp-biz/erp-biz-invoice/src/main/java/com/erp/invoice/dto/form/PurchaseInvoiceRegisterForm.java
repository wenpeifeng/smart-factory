package com.erp.invoice.dto.form;

import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进项发票登记表单（POST /invoice/purchases/from-instock/{instockId}，可空提交）。
 *
 * @param invoiceNo   票面发票号码（8 位数字；缺省由系统取号）
 * @param invoiceDate 票面开票日期（缺省取入库日期）
 * @param taxRate     税率（百分数，仅允许 13 / 9 / 6；缺省取入库明细统一税率，否则 13）
 * @param remark      备注
 */
public record PurchaseInvoiceRegisterForm(
        @Pattern(regexp = "^\\d{8}$", message = "发票号码必须为8位数字") String invoiceNo,
        LocalDate invoiceDate,
        BigDecimal taxRate,
        @Pattern(regexp = "^\\S.{0,499}$", message = "备注不能以空白开头且不超过500字符") String remark) {
}
