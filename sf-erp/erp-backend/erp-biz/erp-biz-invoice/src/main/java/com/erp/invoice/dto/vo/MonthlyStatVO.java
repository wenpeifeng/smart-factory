package com.erp.invoice.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 年度 12 个月价税走势 VO（GET /invoice/statistics/monthly）。
 *
 * @param year   年度
 * @param months 12 行（1-12 月固定补齐，无发生额月份为 0）
 */
public record MonthlyStatVO(int year, List<MonthRow> months) {

    /**
     * 单月行。
     *
     * @param month         月份（1-12）
     * @param salesTotal    销项价税合计（净额）
     * @param salesTax      销项税额（净额）
     * @param purchaseTotal 进项价税合计（净额）
     * @param purchaseTax   进项税额（净额）
     */
    public record MonthRow(int month, BigDecimal salesTotal, BigDecimal salesTax,
                           BigDecimal purchaseTotal, BigDecimal purchaseTax) {
    }
}
