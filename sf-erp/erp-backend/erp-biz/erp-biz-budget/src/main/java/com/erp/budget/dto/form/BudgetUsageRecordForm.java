package com.erp.budget.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预算执行数登记表单（POST /budget/usage）。
 * <p>
 * 即 {@code BudgetControlService.record(...)} 的表单形态：finance 模块付款单
 * 创建/审核时按接线约定调用（sourceType=PAYMENT，带 sourceId/sourceNo）；
 * 亦用于手工登记与演示场景（冲销用负数金额 → 改用 MANUAL + 负数）。
 */
@Data
public class BudgetUsageRecordForm {

    /** 部门 ID（sys_dept.id） */
    @NotNull(message = "部门不能为空")
    private Long deptId;

    /** 科目 ID（fin_subject.id，费用/成本类） */
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

    /** 发生金额（冲销/调整可传负数） */
    @NotNull(message = "发生金额不能为空")
    private BigDecimal amount;

    /** 来源类型：PAYMENT付款 / VOUCHER凭证 / MANUAL手工 */
    @Pattern(regexp = "PAYMENT|VOUCHER|MANUAL", message = "来源类型只允许 PAYMENT/VOUCHER/MANUAL")
    private String sourceType = "MANUAL";

    /** 来源单据 ID（如 fin_payment.id） */
    private Long sourceId;

    /** 来源单据号（如 FK202609050001） */
    @Size(max = 40, message = "来源单据号长度不能超过 40 字符")
    private String sourceNo;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200 字符")
    private String remark;
}
