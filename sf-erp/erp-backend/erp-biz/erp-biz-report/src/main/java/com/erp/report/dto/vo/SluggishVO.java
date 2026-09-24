package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 呆滞物料分析 VO。
 * <p>
 * 呆滞判定：现存量 &gt; 0 且 days 天内无出库方向流水（无任何出库流水视为呆滞）；
 * 库龄锚点 = 最后一次入库流水时间；金额 = 现存量 × 加权价（回退 ref_cost）。
 */
@Data
public class SluggishVO {

    /** 呆滞天数阈值 */
    private Integer days;

    /** 呆滞金额合计（元） */
    private BigDecimal totalValue;

    /** 呆滞行（按金额降序） */
    private List<Row> rows;

    /** 单行呆滞物料 */
    @Data
    public static class Row {

        private Long materialId;

        private String materialCode;

        private String materialName;

        /** 计量单位名称 */
        private String unitName;

        /** 现存量（跨仓合计） */
        private BigDecimal qty;

        /** 加权价（4 位，回退 ref_cost） */
        private BigDecimal price;

        /** 呆滞金额（元） */
        private BigDecimal value;

        /** 金额占比（0~1，4 位 HALF_UP；按呆滞合计为分母） */
        private BigDecimal ratio;

        /** 最后出库时间 yyyy-MM-dd（null = 从未出库） */
        private String lastOutTime;

        /** 距最后一次出库天数（null = 从未出库） */
        private Integer daysSinceOut;

        /** 最后入库时间 yyyy-MM-dd */
        private String lastInTime;

        /** 库龄天数（null = 无入库流水） */
        private Integer ageDays;
    }
}
