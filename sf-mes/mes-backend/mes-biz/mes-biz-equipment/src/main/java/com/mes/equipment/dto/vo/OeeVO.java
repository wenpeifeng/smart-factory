package com.mes.equipment.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * OEE 计算结果 VO（工作中心维度，时间区间聚合）。
 * <p>OEE = 时间开动率 x 性能开动率 x 合格品率；
 * 数据源：过站记录（工时/数量）+ 工序单件工时。
 */
@Data
public class OeeVO {
    private Long workcenterId;
    private String workcenterName;
    /** 负荷时间（分钟，过站工时合计） */
    private BigDecimal plannedMinutes;
    /** 实际运转时间（分钟，过站工时合计） */
    private BigDecimal runMinutes;
    /** 时间开动率（0-1） */
    private BigDecimal availability;
    /** 性能开动率（0-1，理论工时/实际工时，>1 截断为 1） */
    private BigDecimal performance;
    /** 合格品率（0-1） */
    private BigDecimal quality;
    /** OEE（0-1） */
    private BigDecimal oee;
}
