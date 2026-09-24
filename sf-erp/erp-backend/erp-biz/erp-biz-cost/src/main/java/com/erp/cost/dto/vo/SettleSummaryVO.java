package com.erp.cost.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 存货收发存汇总 VO（月末核算/成本分析共用）。
 */
public record SettleSummaryVO(
        String period,
        String status,
        List<MaterialSummaryRowVO> rows,
        /** 全物料合计 */
        BigDecimal totalBeginAmount,
        BigDecimal totalInAmount,
        BigDecimal totalOutAmount,
        BigDecimal totalEndAmount) {
}
