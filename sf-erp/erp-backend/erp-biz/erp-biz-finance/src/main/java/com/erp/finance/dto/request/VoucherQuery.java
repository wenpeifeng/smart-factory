package com.erp.finance.dto.request;

/**
 * 凭证分页查询条件。
 *
 * @param keyword 凭证号模糊查询
 * @param status  状态：DRAFT / POSTED（精确）
 * @param from    凭证日期起（yyyy-MM-dd，含）
 * @param to      凭证日期止（yyyy-MM-dd，含）
 */
public record VoucherQuery(String keyword, String status, String from, String to) {
}
