package com.erp.asset.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 折旧期间 VO（期间列表行）。
 *
 * @param id          期间 ID
 * @param period      会计期间（yyyyMM）
 * @param status      OPEN-开放 CLOSED-已计提关闭
 * @param voucherId   汇总折旧凭证 ID（无可计提资产时为 null）
 * @param voucherNo   汇总折旧凭证号（回显）
 * @param assetCount  计提资产数
 * @param totalAmount 计提总额
 * @param createTime  创建时间
 * @param updateTime  更新时间
 */
public record DepreciationPeriodVO(
        Long id,
        String period,
        String status,
        Long voucherId,
        String voucherNo,
        Integer assetCount,
        BigDecimal totalAmount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
