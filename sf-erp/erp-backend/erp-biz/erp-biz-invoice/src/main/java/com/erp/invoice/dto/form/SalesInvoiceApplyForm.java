package com.erp.invoice.dto.form;

import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 销项开票申请表单（POST /invoice/sales/from-order/{outstockId}，可空提交）。
 *
 * @param taxRate 税率（百分数，仅允许 13 / 9 / 6；缺省 13）
 * @param remark  备注
 */
public record SalesInvoiceApplyForm(
        BigDecimal taxRate,
        @Pattern(regexp = "^\\S.{0,499}$", message = "备注不能以空白开头且不超过500字符") String remark) {
}
