package com.erp.budget.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算控制配置实体（budget_control_config）—— 每个方案一条。
 * <p>
 * 控制语义（对标金蝶预算控制）：部门×科目×月份 的预算占用率 =
 * (已用 + 本次金额) / 当月预算 × 100%，占用率 ≥ {@link #overPercent} 时按
 * {@link #controlWay} 处理：WARN 仅警告放行；BLOCK 抛业务异常阻止本次业务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_control_config")
public class BudgetControlConfig extends BaseEntity {

    /** 控制方式：超预算警告放行（默认） */
    public static final String WAY_WARN = "WARN";
    /** 控制方式：超预算阻止（抛 BusinessException） */
    public static final String WAY_BLOCK = "BLOCK";

    /** 阈值缺省值：占用率 100% 触发 */
    public static final BigDecimal DEFAULT_OVER_PERCENT = new BigDecimal("100.00");

    /** 方案 ID（budget_plan.id） */
    private Long planId;

    /** 控制方式：WARN警告放行 / BLOCK阻止 */
    private String controlWay;

    /** 超预算阈值百分比（占用率≥该值触发控制，默认 100） */
    private BigDecimal overPercent;
}
