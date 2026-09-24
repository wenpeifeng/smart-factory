package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 工单成本归集行 VO（料工费三块 + 单位成本）。
 */
public record WorkOrderCostRowVO(
        Long workOrderId,
        String orderNo,
        Long materialId,
        String materialCode,
        String materialName,
        BigDecimal qty,
        BigDecimal finishQty,
        String status,
        /** 料：已审核领料 × 移动加权单价 */
        BigDecimal materialCost,
        /** 工：hr_piece_wage_detail 计件工资（表未建成 = 0） */
        BigDecimal laborCost,
        /** 费：制造费用手工分摊 */
        BigDecimal overheadCost,
        /** 料+工+费 */
        BigDecimal totalCost,
        /** 单位成本 = 总成本 / 完工数量（完工为 0 时按订单数量） */
        BigDecimal unitCost) {
}
