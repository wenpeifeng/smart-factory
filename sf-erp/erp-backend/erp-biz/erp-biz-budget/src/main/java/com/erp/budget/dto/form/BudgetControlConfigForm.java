package com.erp.budget.dto.form;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算控制配置保存表单（方案级：控制方式 + 超预算阈值%）。
 */
@Data
public class BudgetControlConfigForm {

    /** 控制方式：WARN警告放行 / BLOCK阻止 */
    @NotBlank(message = "控制方式不能为空")
    @Pattern(regexp = "WARN|BLOCK", message = "控制方式只允许 WARN(警告放行) 或 BLOCK(阻止)")
    private String controlWay;

    /** 超预算阈值百分比（占用率≥该值触发控制） */
    @NotNull(message = "超预算阈值不能为空")
    @DecimalMin(value = "1", message = "超预算阈值不能低于 1%")
    @DecimalMax(value = "1000", message = "超预算阈值不能超过 1000%")
    private BigDecimal overPercent;
}
