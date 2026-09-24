package com.erp.invoice.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期间税负统计 VO（GET /invoice/statistics/tax）。
 * <p>
 * 口径：销项按 invoice_date、进项按 auth_date 归属期间；均只统计
 * 已生效票（待开票/待认证不计），已红冲蓝票仍计入并由红字负数票自然冲减。
 *
 * @param from          期间起（含）
 * @param to            期间止（含）
 * @param salesAmount   不含税销售额（销项净额，红冲冲减后）
 * @param salesTax      销项税额（净额）
 * @param purchaseTax   进项税额（已认证净额）
 * @param vatPayable    应纳增值税 = 销项税额 − 进项税额（可为负，表示留抵）
 * @param taxBurdenRate 税负率（%）= 应纳增值税 ÷ 不含税销售额 × 100，销售额为 0 时返回 0
 */
public record TaxStatVO(LocalDate from, LocalDate to,
                        BigDecimal salesAmount, BigDecimal salesTax, BigDecimal purchaseTax,
                        BigDecimal vatPayable, BigDecimal taxBurdenRate) {
}
