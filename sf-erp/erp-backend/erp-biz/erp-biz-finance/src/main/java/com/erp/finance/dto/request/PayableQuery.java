package com.erp.finance.dto.request;

/**
 * 应付分页查询条件（与应收对称）。
 *
 * @param supplierId    供应商 ID（精确）
 * @param status        状态：UNSETTLED / PARTIAL / SETTLED（精确）
 * @param month         业务月份（yyyy-MM，按 biz_date 区间过滤，非法格式忽略）
 * @param keyword       来源单号模糊查询
 * @param unsettledOnly 仅看未核销完结的单
 */
public record PayableQuery(Long supplierId, String status, String month, String keyword, Boolean unsettledOnly) {
}
