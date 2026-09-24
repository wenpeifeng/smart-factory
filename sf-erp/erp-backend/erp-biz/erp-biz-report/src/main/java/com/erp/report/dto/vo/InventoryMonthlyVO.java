package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收发存月报 VO（物料粒度，金额取 cost_flow 期间汇总）。
 * <p>
 * 守恒关系：期初 + 入库 − 出库 = 期末（数量与金额同时成立，Java 构造期末字段）。
 */
@Data
public class InventoryMonthlyVO {

    /** 月份 yyyy-MM */
    private String month;

    /** 物料行（按物料编码排序） */
    private List<Row> rows;

    /** 合计行 */
    private Total total;

    /** 单行：物料 × 期初/入库/出库/期末（数量 + 金额） */
    @Data
    public static class Row {

        private Long materialId;

        private String materialCode;

        private String materialName;

        /** 计量单位名称 */
        private String unitName;

        /** 期初数量（period &lt; 月累计） */
        private BigDecimal beginQty;

        /** 期初金额（元） */
        private BigDecimal beginAmount;

        /** 入库数量（当月正方向流水） */
        private BigDecimal inQty;

        /** 入库金额（元） */
        private BigDecimal inAmount;

        /** 出库数量（当月负方向流水取正） */
        private BigDecimal outQty;

        /** 出库金额（元，取正） */
        private BigDecimal outAmount;

        /** 期末数量 = beginQty + inQty − outQty */
        private BigDecimal endQty;

        /** 期末金额 = beginAmount + inAmount − outAmount */
        private BigDecimal endAmount;
    }

    /** 合计行（各列全表合计，守恒关系同样成立） */
    @Data
    public static class Total {

        private BigDecimal beginQty;

        private BigDecimal beginAmount;

        private BigDecimal inQty;

        private BigDecimal inAmount;

        private BigDecimal outQty;

        private BigDecimal outAmount;

        private BigDecimal endQty;

        private BigDecimal endAmount;
    }
}
