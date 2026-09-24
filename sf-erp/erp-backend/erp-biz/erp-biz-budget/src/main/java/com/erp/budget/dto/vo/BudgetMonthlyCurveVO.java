package com.erp.budget.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度执行曲线数据点 VO —— 某方案（可筛部门/科目）逐月 预算/已用/执行率。
 */
@Data
public class BudgetMonthlyCurveVO {

    /** 月份（1-12） */
    private Integer month;

    /** 当月预算金额 */
    private BigDecimal budgetAmount;

    /** 当月已用金额（凭证口径 + 登记口径） */
    private BigDecimal usedAmount;

    /** 执行率%（预算为 0 时为 null） */
    private BigDecimal executionRate;
}
