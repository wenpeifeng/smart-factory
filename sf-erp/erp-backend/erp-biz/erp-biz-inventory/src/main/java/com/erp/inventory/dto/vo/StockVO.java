package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 即时库存行 VO（即时库存页 / 预警清单共用字段超集）。
 * <p>
 * 名称/安全库存来自 base_warehouse / base_material 的 LEFT JOIN
 * （基础资料缺失时编码名称为 null，行仍展示）。
 */
@Data
public class StockVO {

    /** inv_stock.id */
    private Long id;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 批次号（非批次物料为空串；批次物料一仓一批次一行，V411 起） */
    private String batchNo;

    /** 库位 ID（0=汇总/未上架行；&gt;0=库位行，V421） */
    private Long locationId;

    /** 库位编码（location_id=0 时为 null，前端显示"-"） */
    private String locationCode;

    /** 库位名称 */
    private String locationName;

    /** 现存量 */
    private BigDecimal qty;

    /** 安全库存（来自 base_material.safety_stock，可为 null） */
    private BigDecimal safetyStock;

    /** 是否低于安全库存（safetyStock 为空时恒为 false） */
    private Boolean belowSafety;

    /** 最近变动时间 */
    private LocalDateTime updateTime;
}
