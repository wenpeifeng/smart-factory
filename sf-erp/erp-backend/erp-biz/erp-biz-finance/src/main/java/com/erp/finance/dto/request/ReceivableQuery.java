package com.erp.finance.dto.request;

/**
 * 应收分页查询条件。
 *
 * @param customerId     客户 ID（精确）
 * @param status         状态：UNSETTLED / PARTIAL / SETTLED（精确）
 * @param month          业务月份（yyyy-MM，按 biz_date 区间过滤，非法格式忽略）
 * @param keyword        来源单号模糊查询
 * @param unsettledOnly  仅看未核销完结的单（amount &gt; settled_amount）
 */
public record ReceivableQuery(Long customerId, String status, String month, String keyword, Boolean unsettledOnly) {
}
