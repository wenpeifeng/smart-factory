package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 备货参考行：物料 × 仓库维度可用量（SUM inv_stock.qty，跨模块只读约定结构）。
 * <p>
 * 供发货通知单发货前查仓：检查计划发货物料在目标仓库是否有足够可用量。
 */
@Data
public class PendingStockVO {

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（JOIN base_material） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String materialSpec;

    /** 仓库 ID */
    private Long warehouseId;

    /** 仓库名称（JOIN base_warehouse） */
    private String warehouseName;

    /** 可用量 = SUM(qty)（含批次行汇总） */
    private BigDecimal availableQty;
}
