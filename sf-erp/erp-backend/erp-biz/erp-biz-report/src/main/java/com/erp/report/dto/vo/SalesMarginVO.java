package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售毛利分析 VO。
 * <p>
 * 成本 = COALESCE(出库明细 actual_cost, qty × base_material.ref_cost)
 * （CostService 记账行取实际成本，历史行回退参考成本）。
 */
@Data
public class SalesMarginVO {

    /** 聚合维度：customer / material */
    private String groupBy;

    /** 聚合行（按销售额降序） */
    private List<Row> rows;

    /** 合计行（维度列空） */
    private Total total;

    /** 单行：维度 × 数量 / 销售额 / 成本 / 毛利 / 毛利率 */
    @Data
    public static class Row {

        /** 维度编码（物料编码或客户编码） */
        private String dimCode;

        /** 维度名称 */
        private String dimName;

        /** 数量 */
        private BigDecimal qty;

        /** 销售额（不含税） */
        private BigDecimal amount;

        /** 销售成本（actual_cost，历史行回退 ref_cost） */
        private BigDecimal costAmount;

        /** 毛利 = amount − costAmount */
        private BigDecimal profitAmount;

        /** 毛利率（%，2 位 HALF_UP；销售额 0 → 0） */
        private BigDecimal marginPct;
    }

    /** 合计行 */
    @Data
    public static class Total {

        private BigDecimal qty;

        private BigDecimal amount;

        private BigDecimal costAmount;

        private BigDecimal profitAmount;

        /** 毛利率（%，合计口径） */
        private BigDecimal marginPct;
    }
}
