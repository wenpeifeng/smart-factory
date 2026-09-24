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
 * 工序定义新增/修改表单（编码由服务层生成，不接受外部传入）。
 */
@Data
public class OperationForm implements Serializable {

    /** 工序名称 */
    @NotBlank(message = "工序名称不能为空")
    @Size(max = 100, message = "名称最长100字符")
    private String name;

    /** 默认工作中心ID（可空） */
    private Long defaultWorkCenterId;

    /** 准备时间（分钟/批） */
    @Min(value = 0, message = "准备时间不能为负数")
    @Max(value = 100000, message = "准备时间最大100000分钟")
    private Integer setupMinutes;

    /** 单位加工时间（分钟/每件） */
    @NotNull(message = "单位加工时间不能为空")
    @DecimalMin(value = "0", message = "单位加工时间不能为负数")
    private BigDecimal unitMinutes;

    /** 外协标志：1外协 0自制 */
    @Min(0)
    @Max(1)
    private Integer outsourceFlag;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
