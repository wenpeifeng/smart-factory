package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 询比价单 VO（列表行与详情通用；详情填充 suppliers/items）。
 */
@Data
public class PurRfqVO {

    /** 询比价单 ID */
    private Long id;

    /** 询比价单号 */
    private String rfqNo;

    /** 来源请购单 ID（可空=独立询价） */
    private Long requisitionId;

    /** 来源请购单号 */
    private String reqNo;

    /** 报价截止日期 */
    private LocalDate deadline;

    /** 状态：0询价中 10已定标 30已取消 */
    private Integer status;

    /** 定标时间 */
    private LocalDateTime awardTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 邀请供应商（详情接口填充） */
    private List<SupplierVO> suppliers;

    /** 询价明细（详情接口填充） */
    private List<ItemVO> items;

    /**
     * 邀请供应商 VO（含报价金额合计与状态）。
     */
    @Data
    public static class SupplierVO {

        /** 询比价供应商行 ID */
        private Long id;

        /** 供应商 ID */
        private Long supplierId;

        /** 供应商名称 */
        private String supplierName;

        /** 报价金额合计 */
        private BigDecimal quoteAmount;

        /** 状态：0已邀请 10已报价 20中标 */
        private Integer status;
    }

    /**
     * 询价明细 VO（含定标结果与下推订单回写）。
     */
    @Data
    public static class ItemVO {

        /** 明细 ID */
        private Long id;

        /** 来源请购明细 ID */
        private Long requisitionItemId;

        /** 物料 ID */
        private Long materialId;

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

        /** 定标供应商 ID */
        private Long awardSupplierId;

        /** 中标单价 */
        private BigDecimal awardPrice;

        /** 中标交期（天） */
        private Integer awardLeadDays;

        /** 下推生成的采购订单 ID */
        private Long purOrderId;

        /** 备注 */
        private String remark;
    }
}
