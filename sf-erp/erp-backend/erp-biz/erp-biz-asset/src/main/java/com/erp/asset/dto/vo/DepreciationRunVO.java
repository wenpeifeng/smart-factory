package com.erp.asset.dto.vo;

import java.math.BigDecimal;

/**
 * 折旧计提执行结果 VO。
 *
 * @param period      期间（yyyyMM）
 * @param assetCount  本次计提资产数
 * @param totalAmount 本次计提总额
 * @param voucherId   汇总凭证 ID（无可计提资产时为 null）
 * @param voucherNo   汇总凭证号
 * @param message     执行说明（如"无符合条件的资产"）
 */
public record DepreciationRunVO(
        String period,
        Integer assetCount,
        BigDecimal totalAmount,
        Long voucherId,
        String voucherNo,
        String message) {
}
