package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 询比价【比价矩阵】VO：物料 × 供应商 矩阵。
 * <p>
 * 每个明细行挂全部供应商的报价格（未报价为 null），
 * lowest 标记该物料下最低报价的供应商（并列最低都标记），
 * 前端据此高亮辅助分项定标决策。
 */
@Data
public class RfqComparisonVO {

    /** 询比价单 ID */
    private Long rfqId;

    /** 询比价单号 */
    private String rfqNo;

    /** 状态：0询价中 10已定标 30已取消 */
    private Integer status;

    /** 报价截止日期 */
    private String deadline;

    /** 邀请供应商列头（含报价状态与合计） */
    private List<SupplierHead> suppliers;

    /** 矩阵行：每物料一行 */
    private List<ItemRow> items;

    /**
     * 供应商列头。
     */
    @Data
    public static class SupplierHead {

        /** 供应商 ID */
        private Long supplierId;

        /** 供应商名称 */
        private String supplierName;

        /** 状态：0已邀请 10已报价 20中标 */
        private Integer status;

        /** 报价金额合计 */
        private BigDecimal quoteAmount;
    }

    /**
     * 矩阵行（一物料）。
     */
    @Data
    public static class ItemRow {

        /** 询比价明细 ID */
        private Long itemId;

        /** 物料编码 */
        private String materialCode;

        /** 物料名称 */
        private String materialName;

        /** 规格 */
        private String spec;

        /** 计量单位 */
        private String unit;

        /** 询价数量 */
        private BigDecimal qty;

        /** 定标供应商 ID（已定标时回显） */
        private Long awardSupplierId;

        /** 各供应商报价格（未报价的供应商不在列表中） */
        private List<QuoteCell> quotes;
    }

    /**
     * 报价格（一供应商对一物料的报价）。
     */
    @Data
    public static class QuoteCell {

        /** 供应商 ID */
        private Long supplierId;

        /** 报价单价（含税） */
        private BigDecimal price;

        /** 交期（天） */
        private Integer leadDays;

        /** 总价 = 单价 × 询价数量（HALF_UP 2 位，服务端计算） */
        private BigDecimal total;

        /** 是否该物料最低价（并列最低都为 true） */
        private boolean lowest;
    }
}
