package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mrp_work_order 只读投影（工单成本归集列表页）。
 */
@Data
public class WorkOrderRaw {

    /** mrp_work_order.id */
    private Long id;

    /** 工单号 */
    private String orderNo;

    /** 成品物料 ID */
    private Long materialId;

    /** 成品物料编码（base_material 回显） */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 订单数量 */
    private BigDecimal qty;

    /** 完工数量 */
    private BigDecimal finishQty;

    /** 订单状态 */
    private String status;

    /** 计划开工日 */
    private LocalDate planStartDate;

    /** 计划完工日 */
    private LocalDate planEndDate;
}
