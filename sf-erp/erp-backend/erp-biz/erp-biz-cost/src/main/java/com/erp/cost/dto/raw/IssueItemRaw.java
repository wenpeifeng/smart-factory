package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 生产领料只读投影（工单 × 物料 汇总粒度）。
 */
@Data
public class IssueItemRaw {

    /** 生产订单 ID */
    private Long workOrderId;

    /** 子件物料 ID */
    private Long materialId;

    /** 子件物料编码 */
    private String materialCode;

    /** 子件物料名称 */
    private String materialName;

    /** 实领数量合计（mrp_material_issue_item.actual_qty，仅已审核领料单） */
    private BigDecimal qty;
}
