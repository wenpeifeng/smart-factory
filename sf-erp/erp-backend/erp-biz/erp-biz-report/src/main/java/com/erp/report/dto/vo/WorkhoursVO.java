package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报工工时统计 VO（mrp_work_report）。
 * <p>
 * groupBy=operation：维度 = 工序，extra = 参与员工数；
 * groupBy=employee：维度 = 报工员工，extra = 涉及工序数。
 * 工时 = work_minutes 合计（分钟），workHours = 分钟 / 60（2 位 HALF_UP）。
 */
@Data
public class WorkhoursVO {

    /** 聚合维度：operation / employee */
    private String groupBy;

    /** 聚合行（按工时降序） */
    private List<Row> rows;

    /** 单行报工聚合 */
    @Data
    public static class Row {

        /** 维度编码（工序编码或员工姓名） */
        private String dimCode;

        /** 维度名称（工序名称或员工姓名） */
        private String dimName;

        /** 附加维度计数（operation=员工数 / employee=工序数） */
        private Long extraCount;

        /** 合格数 */
        private BigDecimal goodQty;

        /** 不良数 */
        private BigDecimal badQty;

        /** 不良率（%，2 位） */
        private BigDecimal badRate;

        /** 工时（分钟） */
        private Long workMinutes;

        /** 工时（小时 = 分钟/60，2 位 HALF_UP） */
        private BigDecimal workHours;
    }
}
