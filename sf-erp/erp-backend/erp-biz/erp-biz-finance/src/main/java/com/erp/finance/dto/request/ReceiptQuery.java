package com.erp.finance.dto.request;

/**
 * 收款单分页查询条件。
 *
 * @param customerId 客户 ID（精确）
 * @param month      收款月份（yyyy-MM，非法格式忽略）
 * @param keyword    收款单号模糊查询
 */
public record ReceiptQuery(Long customerId, String month, String keyword) {
}
