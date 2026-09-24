package com.erp.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计量单位新增/修改表单。
 * <p>不直接接收 Entity，防 mass assignment（见开发规范 1.2）。
 */
@Data
public class BaseUnitForm {

    /** 换算组编码（同组单位互相换算） */
    @NotBlank(message = "换算组编码不能为空")
    @Size(max = 30, message = "换算组编码长度不能超过30")
    private String groupCode;

    /** 单位名称 */
    @NotBlank(message = "单位名称不能为空")
    @Size(max = 30, message = "单位名称长度不能超过30")
    private String name;

    /** 换算比率（相对组内基准单位），必须大于 0 */
    @NotNull(message = "换算比率不能为空")
    @DecimalMin(value = "0.000001", message = "换算比率必须大于0")
    private BigDecimal rate;

    /** 是否基准单位：1-是 0-否 */
    @NotNull(message = "是否基准单位不能为空")
    private Integer isBase;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
