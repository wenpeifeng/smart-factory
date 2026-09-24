package com.erp.budget.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算校验请求表单（POST /budget/check）。
 * <p>
 * 模拟付款/费用发生前的预算占用校验：占用率 = (已用 + 本次金额)/当月预算。
 */
@Data
public class BudgetCheckForm {

    /** 部门 ID（sys_dept.id） */
    @NotNull(message = "部门不能为空")
    private Long deptId;

    /** 科目 ID（fin_subject.id） */
    @NotNull(message = "科目不能为空")
    private Long subjectId;

    /** 年度 */
    @NotNull(message = "年度不能为空")
    private Integer year;

    /** 月份（1-12） */
    @NotNull(message = "月份不能为空")
    @Min(value = 1, message = "月份范围为 1-12")
    @Max(value = 12, message = "月份范围为 1-12")
    private Integer month;

    /** 本次发生金额（>0） */
    @NotNull(message = "校验金额不能为空")
    @DecimalMin(value = "0.01", message = "校验金额必须大于 0")
    private BigDecimal amount;
}
