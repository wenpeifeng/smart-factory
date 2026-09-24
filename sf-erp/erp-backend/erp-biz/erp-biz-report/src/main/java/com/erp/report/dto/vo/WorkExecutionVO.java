package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 生产订单执行跟踪 VO。
 * <p>
 * 完工率 = finish_qty / qty × 100（2 位；计划量 0 → 0）；
 * 在制 = qty − finish_qty（负数截 0）；
 * 超期 = status ∉ {FINISHED, CLOSED} 且 plan_end_date &lt; 今天。
 */
@Data
public class WorkExecutionVO {

    /** 工单行（超期优先，再按工单号） */
    private List<Row> rows;

    /** 单行工单执行情况 */
    @Data
    public static class Row {

        private Long orderId;

        private String orderNo;

        private String materialCode;

        private String materialName;

        /** 计划量 */
        private BigDecimal planQty;

        /** 完工量（良品） */
        private BigDecimal finishQty;

        /** 在制 = max(planQty − finishQty, 0) */
        private BigDecimal wipQty;

        /** 完工率（%，2 位） */
        private BigDecimal finishRate;

        /** 工单状态编码（DRAFT/RELEASED/PRODUCING/FINISHED/CLOSED） */
        private String status;

        /** 状态中文 */
        private String statusLabel;

        /** 计划开工日期 yyyy-MM-dd */
        private String planStartDate;

        /** 计划完工日期 yyyy-MM-dd */
        private String planEndDate;

        /** 是否超期 */
        private Boolean overdue;
    }
}
