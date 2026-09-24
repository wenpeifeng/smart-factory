package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 能力计划(CRP 简版)单行 VO —— 一个工作中心在统计期间的负荷对比。
 * <p>
 * 口径：负荷工时 = 未完工 mrp_wo_step 的 计划数量×单件工时 汇总（不按期间切分，简版口径）；
 * 可用工时 = 每日产能 × 工作日数（周六周日不计）；负荷率 = 负荷/可用×100，>100 为超载。
 */
@Data
public class CapacityLoadVO implements Serializable {

    /** 工作中心ID */
    private Long workCenterId;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 所属车间 */
    private String workshop;

    /** 状态 */
    private String status;

    /** 负荷工时（小时） */
    private BigDecimal loadHours;

    /** 可用工时（小时，= 每日产能×工作日数） */
    private BigDecimal availableHours;

    /** 负荷率%（保留1位；可用为0且有负荷时为空，前端按超载展示） */
    private BigDecimal loadRate;

    /** 是否超载（负荷率>100） */
    private Boolean overloaded;

    /** 未完工任务数 */
    private Long pendingStepCount;
}
