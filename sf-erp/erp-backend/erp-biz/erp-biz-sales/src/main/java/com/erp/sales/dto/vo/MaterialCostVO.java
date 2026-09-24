package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料成本简要 VO（base_material 只读跨模块查询）：
 * id + ref_cost，用于报价明细录入时取成本快照。
 */
@Data
public class MaterialCostVO {

    /** 物料 ID */
    private Long id;

    /** 参考成本（base_material.ref_cost） */
    private BigDecimal refCost;
}
