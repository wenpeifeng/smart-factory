package com.erp.finance.dto.request;

/**
 * 付款单分页查询条件（与收款对称）。
 *
 * @param supplierId 供应商 ID（精确）
 * @param month      付款月份（yyyy-MM，非法格式忽略）
 * @param keyword    付款单号模糊查询
 */
public record PaymentQuery(Long supplierId, String month, String keyword) {
}
