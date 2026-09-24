package com.erp.cost.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 存货核算期间行 VO。
 */
public record CostPeriodVO(
        Long id,
        String period,
        String status,
        BigDecimal outAmount,
        Long voucherId,
        String voucherNo,
        Long settleBy,
        LocalDateTime settleTime,
        String remark) {
}
