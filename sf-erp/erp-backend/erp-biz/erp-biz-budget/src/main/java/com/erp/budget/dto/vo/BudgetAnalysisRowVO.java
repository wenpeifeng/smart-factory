package com.erp.budget.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算执行分析行 VO —— 部门×科目 的 预算/已用/余额/执行率（全年口径）。
 */
@Data
public class BudgetAnalysisRowVO {

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

    /** 全年预算金额 */
    private BigDecimal budgetAmount;

    /** 全年已用金额（凭证口径 + 登记口径） */
    private BigDecimal usedAmount;

    /** 余额（预算 - 已用） */
    private BigDecimal balanceAmount;

    /** 执行率%（已用/预算×100，保留 2 位；预算为 0 时为 null） */
    private BigDecimal executionRate;
}
