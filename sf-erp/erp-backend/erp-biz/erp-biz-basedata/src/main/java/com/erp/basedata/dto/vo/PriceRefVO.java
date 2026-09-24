package com.erp.basedata.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购录单价格参照 VO（GET /basedata/prices/last 的响应）。
 * <p>
 * 合同价优先展示（标注"合同"）；last 为价格流水最近一次成交价。
 */
@Data
public class PriceRefVO {

    /** 供应商 ID */
    private Long supplierId;

    /** 物料 ID */
    private Long materialId;

    /** 生效合同价（无合同为 null） */
    private ContractItem contract;

    /** 最近一次成交价（无记录为 null） */
    private LastItem last;

    /** 合同价条目 */
    @Data
    public static class ContractItem {

        /** 合同价 ID */
        private Long id;

        /** 合同单价（不含税） */
        private BigDecimal price;

        /** 生效起（含） */
        private LocalDate effectiveFrom;

        /** 生效止（含） */
        private LocalDate effectiveTo;

        /** 备注（合同号等） */
        private String remark;
    }

    /** 最近成交条目 */
    @Data
    public static class LastItem {

        /** 流水 ID */
        private Long id;

        /** 成交单价（不含税） */
        private BigDecimal price;

        /** 来源类型 */
        private String sourceType;

        /** 来源类型中文 */
        private String sourceTypeLabel;

        /** 来源单号 */
        private String sourceNo;

        /** 价格日期 */
        private LocalDate priceDate;
    }
}
