package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 暂估报表 VO（GET /purchase/estimations/summary?month=yyyyMM）。
 * <p>
 * 口径：按 pur_estimation.period（暂估期间）归属——当月暂估总额 / 已冲回 / 未冲回余额，
 * 并按供应商维度展开；暂估余额 = 未冲回余额（已冲回部分不再挂账）。
 */
@Data
public class PurEstimationSummaryVO {

    /** 暂估期间 yyyyMM */
    private String period;

    /** 当月暂估总额（Σ amount） */
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 当月已冲回金额（Σ reversed_amount） */
    private BigDecimal reversedAmount = BigDecimal.ZERO;

    /** 未冲回余额 = 暂估余额（Σ amount - Σ reversed_amount） */
    private BigDecimal openAmount = BigDecimal.ZERO;

    /** 供应商维度明细行 */
    private List<SupplierRow> suppliers = new ArrayList<>();

    /** 供应商汇总行 */
    @Data
    public static class SupplierRow {

        private Long supplierId;

        /** 供应商名称（LEFT JOIN，可能为 null） */
        private String supplierName;

        /** 暂估单数 */
        private Long estimationCount;

        /** 暂估总额 */
        private BigDecimal totalAmount;

        /** 已冲回金额 */
        private BigDecimal reversedAmount;

        /** 未冲回余额 */
        private BigDecimal openAmount;
    }
}
