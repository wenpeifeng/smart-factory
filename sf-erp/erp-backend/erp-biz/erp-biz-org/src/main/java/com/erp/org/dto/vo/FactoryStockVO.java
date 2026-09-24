package com.erp.org.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工厂库存统计行（工厂下各仓库库存量汇总，inv_stock × base_warehouse 只读聚合）。
 */
@Data
public class FactoryStockVO {

    /** 仓库 ID */
    private Long warehouseId;

    /** 仓库编码 */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 有库存记录的物料种数 */
    private Long materialCount;

    /** 库存量合计 */
    private BigDecimal totalQty;
}
