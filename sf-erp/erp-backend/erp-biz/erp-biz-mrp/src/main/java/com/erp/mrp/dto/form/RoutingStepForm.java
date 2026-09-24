package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺路线步骤表单行 —— 工作中心/单件工时不传时取工序默认值。
 */
@Data
public class RoutingStepForm implements Serializable {

    /** 序号（工序顺序，从1起） */
    @NotNull(message = "步骤序号不能为空")
    @Min(value = 1, message = "步骤序号从1起")
    private Integer seqNo;

    /** 工序ID */
    @NotNull(message = "步骤工序不能为空")
    private Long operationId;

    /** 工作中心ID（空=取工序默认） */
    private Long workCenterId;

    /** 单件工时分钟（空=取工序默认） */
    @DecimalMin(value = "0", message = "单件工时不能为负数")
    private BigDecimal unitMinutes;

    /** 是否报工点：1是 0否（默认1） */
    @Min(0)
    @Max(1)
    private Integer reportFlag;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
