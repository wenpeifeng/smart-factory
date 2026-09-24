package com.erp.finance.dto.vo;

/**
 * 期间凭证数聚合原始行（FinPeriodMapper.selectPeriodStats 结果，V521）。
 * <p>
 * record 按列序位置映射（别名与参数名一致）。
 *
 * @param period      期间（yyyyMM）
 * @param voucherCount 凭证总数
 * @param draftCount  未过账（DRAFT）数
 * @param postedCount 已过账（POSTED）数
 */
public record PeriodStatRowVO(String period, Long voucherCount, Long draftCount, Long postedCount) {
}
