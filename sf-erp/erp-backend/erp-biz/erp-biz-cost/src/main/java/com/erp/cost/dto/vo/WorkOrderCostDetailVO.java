package com.erp.cost.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工单成本详情 VO（GET /cost/work-orders/{id}）。
 */
public record WorkOrderCostDetailVO(
        Long workOrderId,
        String orderNo,
        Long materialId,
        String materialCode,
        String materialName,
        BigDecimal qty,
        BigDecimal finishQty,
        String status,
        LocalDate planStartDate,
        LocalDate planEndDate,
        /** 领料明细（料） */
        List<IssueCostLineVO> issueLines,
        /** 计件工资明细（工，hr 约定表未建成返回空列表） */
        List<WageLineVO> wageLines,
        /** 制造费用分摊（费，手工录入） */
        BigDecimal overheadAmount,
        BigDecimal laborHours,
        String remark,
        BigDecimal materialCost,
        BigDecimal laborCost,
        BigDecimal overheadCost,
        BigDecimal totalCost,
        BigDecimal unitCost) {
}
