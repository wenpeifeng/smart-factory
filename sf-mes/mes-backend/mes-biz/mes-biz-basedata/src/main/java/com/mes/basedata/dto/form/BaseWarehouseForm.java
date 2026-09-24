package com.mes.basedata.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 仓库新增/修改表单。
 * <p>编码 code 可留空，留空时按「CK+日期+3位流水」自动生成。
 */
@Data
public class BaseWarehouseForm {

    /** 仓库编码（留空自动生成） */
    @Size(max = 40, message = "仓库编码长度不能超过40")
    private String code;

    /** 仓库名称 */
    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 100, message = "仓库名称长度不能超过100")
    private String name;

    /** 负责人 */
    @Size(max = 50, message = "负责人长度不能超过50")
    private String manager;

    /** 地址 */
    @Size(max = 200, message = "地址长度不能超过200")
    private String address;

    /** 是否启用：1-是 0-否 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
