package com.erp.basedata.dto.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 结算方式新增/修改表单。
 */
@Data
public class BaseSettleTypeForm {

    /** 方式编码（如 XJ/ZZ/CD/YJ） */
    @NotBlank(message = "方式编码不能为空")
    @Size(max = 20, message = "方式编码长度不能超过20")
    private String code;

    /** 方式名称 */
    @NotBlank(message = "方式名称不能为空")
    @Size(max = 50, message = "方式名称长度不能超过50")
    private String name;

    /** 账期天数，不能为负 */
    @Min(value = 0, message = "账期天数不能为负数")
    private Integer days;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
