package com.erp.workflow.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预警扫描行 —— 库存低于安全库存（base_material vs inv_stock 汇总，只读投影）。
 */
@Data
public class StockWarnRow {

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 当前总库存（全仓汇总） */
    private BigDecimal totalQty;
}
