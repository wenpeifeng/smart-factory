package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料数量汇总行（MRP 运算取库存/在途汇总口径的通用载体）。
 */
@Data
public class MaterialQtyVO implements Serializable {

    /** 物料ID */
    private Long materialId;

    /** 汇总数量 */
    private BigDecimal qty;
}
