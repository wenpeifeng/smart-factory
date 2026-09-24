package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计件工资汇总 VO（hr_piece_wage × detail，员工 × 工序）。
 * <p>
 * 期间无工资表（hr_piece_wage 无该 period 记录）时 rows 为空列表。
 */
@Data
public class PiecewageVO {

    /** 期间 yyyy-MM */
    private String period;

    /** 员工 × 工序行 */
    private List<Row> rows;

    /** 合计行 */
    private Total total;

    /** 单行工资 */
    @Data
    public static class Row {

        private Long employeeId;

        private String empNo;

        private String employeeName;

        /** 工序名称（计件价目维度） */
        private String processName;

        /** 计件产量 */
        private BigDecimal qty;

        /** 计件工资（元） */
        private BigDecimal amount;

        /** 涉及工单数 */
        private Long woCount;
    }

    /** 合计行 */
    @Data
    public static class Total {

        private BigDecimal qty;

        private BigDecimal amount;
    }
}
