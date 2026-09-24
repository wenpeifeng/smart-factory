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
 * 工作中心新增/修改表单（编码由服务层生成，不接受外部传入）。
 */
@Data
public class WorkCenterForm implements Serializable {

    /** 工作中心名称 */
    @NotBlank(message = "工作中心名称不能为空")
    @Size(max = 100, message = "名称最长100字符")
    private String name;

    /** 所属车间（文本） */
    @Size(max = 100, message = "所属车间最长100字符")
    private String workshop;

    /** 每日产能工时（小时/天） */
    @NotNull(message = "每日产能工时不能为空")
    @DecimalMin(value = "0.01", message = "每日产能工时必须大于0")
    private BigDecimal capacityHoursPerDay;

    /** 设备数 */
    @NotNull(message = "设备数不能为空")
    @Min(value = 0, message = "设备数不能为负数")
    @Max(value = 9999, message = "设备数最大9999")
    private Integer equipmentCount;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
