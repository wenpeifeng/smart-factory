package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工序报工表单 —— 一次报工：合格数/不合格数/实际工时，事务内回写工序任务状态。
 */
@Data
public class WorkReportForm implements Serializable {

    /** 工单工序任务ID */
    @NotNull(message = "工序任务不能为空")
    private Long woStepId;

    /** 报工员工姓名 */
    @NotBlank(message = "报工员工不能为空")
    @Size(max = 50, message = "员工姓名最长50字符")
    private String employee;

    /** 合格数 */
    @NotNull(message = "合格数不能为空")
    @DecimalMin(value = "0", message = "合格数不能为负数")
    private BigDecimal goodQty;

    /** 不合格数 */
    @NotNull(message = "不合格数不能为空")
    @DecimalMin(value = "0", message = "不合格数不能为负数")
    private BigDecimal badQty;

    /** 实际工时（分钟） */
    @Min(value = 0, message = "实际工时不能为负数")
    @Max(value = 1000000, message = "实际工时最大1000000分钟")
    private Integer workMinutes;

    /** 质量待处理说明（不合格时建议填写；QC 挂点） */
    @Size(max = 500, message = "质量待处理说明最长500字符")
    private String qcNote;
}
