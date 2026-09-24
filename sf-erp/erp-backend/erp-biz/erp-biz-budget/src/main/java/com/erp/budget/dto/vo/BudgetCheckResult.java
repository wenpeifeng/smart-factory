package com.erp.budget.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算校验结果（{@code BudgetControlService.check} 的返回值）。
 * <p>
 * level 语义：OK 预算内放行；WARN 超阈值但控制方式为警告（放行并提示）；
 * BLOCK 超阈值且控制方式为阻止 —— 该场景 check 直接抛 BusinessException，
 * 本对象仅在 OK/WARN 场景返回（BLOCK 字段值供异常消息组装复用）。
 */
@Data
public class BudgetCheckResult {

    /** 校验级别：OK 预算内 / WARN 超阈值警告放行 / BLOCK 超阈值阻止 */
    private String level;

    /** 是否放行（WARN 放行=true；BLOCK 场景抛异常不返回） */
    private boolean passed;

    /** 部门 ID */
    private Long deptId;

    /** 部门名称 */
    private String deptName;

    /** 科目 ID */
    private Long subjectId;

    /** 科目编码 */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 年度 */
    private Integer year;

    /** 月份 */
    private Integer month;

    /** 当月预算金额 */
    private BigDecimal budgetAmount;

    /** 校验前已用金额（凭证口径 + 登记口径） */
    private BigDecimal usedAmount;

    /** 本次校验金额 */
    private BigDecimal checkAmount;

    /** 校验后累计占用（已用 + 本次） */
    private BigDecimal totalAfterCheck;

    /** 预算占用率%（(已用+本次)/预算×100，保留 2 位） */
    private BigDecimal ratioPercent;

    /** 控制方式：WARN/BLOCK */
    private String controlWay;

    /** 超预算阈值百分比 */
    private BigDecimal thresholdPercent;

    /** 结果说明（提示文案） */
    private String message;
}
