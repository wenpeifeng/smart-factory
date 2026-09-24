package com.erp.finance.dto.vo;

import java.time.LocalDateTime;

/**
 * 总账会计期间 VO（V521）。
 *
 * @param period       期间（yyyyMM）
 * @param status       状态：OPEN 开启 / CLOSED 已结账
 * @param voucherCount 该期凭证总数（当前账套，含 DRAFT/POSTED，不含已删）
 * @param draftCount   该期未过账（DRAFT）凭证数
 * @param postedCount  该期已过账（POSTED）凭证数
 * @param closeBy      结账人
 * @param closeTime    结账时间
 * @param reopenBy     最近反结账人
 * @param reopenTime   最近反结账时间
 */
public record PeriodVO(String period, String status, Long voucherCount, Long draftCount,
                       Long postedCount, Long closeBy, LocalDateTime closeTime,
                       Long reopenBy, LocalDateTime reopenTime) {
}
