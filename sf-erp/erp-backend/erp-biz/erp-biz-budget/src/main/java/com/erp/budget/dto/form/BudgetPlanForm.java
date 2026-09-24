package com.erp.budget.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 预算方案新增/修改表单（方案基本信息，明细矩阵走 MatrixForm）。
 */
@Data
public class BudgetPlanForm {

    /** 方案编码（全局唯一，建议 YS+年度+序号） */
    @NotBlank(message = "方案编码不能为空")
    @Size(max = 40, message = "方案编码长度不能超过 40 字符")
    private String planCode;

    /** 方案名称 */
    @NotBlank(message = "方案名称不能为空")
    @Size(max = 100, message = "方案名称长度不能超过 100 字符")
    private String planName;

    /** 预算年度 */
    @NotNull(message = "预算年度不能为空")
    @Min(value = 2000, message = "预算年度不能早于 2000 年")
    @Max(value = 2100, message = "预算年度不能晚于 2100 年")
    private Integer budgetYear;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500 字符")
    private String remark;
}
