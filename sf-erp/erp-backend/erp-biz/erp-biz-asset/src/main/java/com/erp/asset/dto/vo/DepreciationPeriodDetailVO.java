package com.erp.asset.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 折旧期间详情 VO（期间头 + 明细行 + 合计）。
 */
public record DepreciationPeriodDetailVO(
        Long id,
        String period,
        String status,
        Long voucherId,
        String voucherNo,
        Integer assetCount,
        BigDecimal totalAmount,
        List<DepreciationDetailVO> details) {
}
