package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 安全库存预警行 VO。
 * <p>
 * 以 base_material 为主表 LEFT JOIN inv_stock：
 * 物料从未有过库存（无库存行）按 0 计，同样参与预警。
 */
@Data
public class WarningVO {

    /** 关联的 inv_stock.id（从未有库存时为 null） */
    private Long stockId;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 现存量（无库存行按 0） */
    private BigDecimal qty;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 缺口 = 安全库存 - 现存量（正数） */
    private BigDecimal shortage;
}
