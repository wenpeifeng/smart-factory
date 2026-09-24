package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商绩效分析 VO（对标金蝶供应商综合评估）。
 * <p>
 * 采购额/订单数取采购订单（status 10/20/30）；到货及时率 = 已审核入库明细行中
 * instock_date ≤ COALESCE(订单行 delivery_date, 订单 order_date) 的行占比；
 * 质检合格率 = IQC（source_type='pur_instock'）qualified_qty / sample_qty，
 * 无质检数据时 rate 字段为 null（前端展示 N/A）。
 */
@Data
public class SupplierPerformanceVO {

    /** 供应商行（按入库金额降序） */
    private List<Row> rows;

    /** 单行供应商绩效 */
    @Data
    public static class Row {

        private Long supplierId;

        private String supplierCode;

        private String supplierName;

        /** 订单数（已审核/部分入库/完成入库的采购订单） */
        private Long orderCount;

        /** 订单金额（元） */
        private BigDecimal orderAmount;

        /** 入库金额（已审核采购入库价税合计，元） */
        private BigDecimal instockAmount;

        /** 到货明细行数（参与及时率分母） */
        private Long receiptLineCount;

        /** 到货及时行数（instock_date ≤ 计划交货日期） */
        private Long onTimeLineCount;

        /** 到货及时率（%，2 位；无到货行 → null = N/A） */
        private BigDecimal onTimeRate;

        /** IQC 抽检数量合计 */
        private BigDecimal sampleQty;

        /** IQC 合格数量合计 */
        private BigDecimal qualifiedQty;

        /** 质检合格率（%，2 位；无质检数据 → null = N/A） */
        private BigDecimal qualifiedRate;
    }
}
