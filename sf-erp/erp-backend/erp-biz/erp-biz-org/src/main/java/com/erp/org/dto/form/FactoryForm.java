package com.erp.org.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工厂档案新增/修改表单。
 * <p>
 * code 留空由服务端自动生成（FC+yyyyMMdd+3位流水）；显式传入须符合编码规则。
 */
@Data
public class FactoryForm {

    /** 工厂编码（留空自动生成） */
    @Size(max = 40, message = "工厂编码长度不能超过40")
    @Pattern(regexp = "^$|^[A-Za-z0-9_-]{2,40}$", message = "工厂编码仅允许字母/数字/下划线/中划线")
    private String code;

    /** 工厂名称 */
    @NotBlank(message = "工厂名称不能为空")
    @Size(max = 100, message = "工厂名称长度不能超过100")
    private String name;

    /** 工厂地址 */
    @Size(max = 200, message = "工厂地址长度不能超过200")
    private String address;

    /** 负责人 */
    @Size(max = 50, message = "负责人长度不能超过50")
    private String manager;

    /** 状态：1 启用 0 停用（默认 1） */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
