package com.mes.system.dto.role;

import com.mes.system.dto.Create;
import com.mes.system.dto.Update;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增/修改表单。
 */
@Data
public class RoleForm {

    /** 角色 ID（修改时由路径参数注入，不做 JSR-303 约束） */
    private Long id;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空", groups = {Create.class, Update.class})
    @Size(max = 30, message = "角色名称最长 30 字", groups = {Create.class, Update.class})
    private String name;

    /** 角色编码：字母开头，字母/数字/下划线 */
    @NotBlank(message = "角色编码不能为空", groups = {Create.class, Update.class})
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$", message = "编码须为字母开头的字母/数字/下划线，2-50 位",
            groups = {Create.class, Update.class})
    private String code;

    /** 显示顺序 */
    private Integer sort;

    /** 状态：1-启用 0-停用；null 时服务端按启用处理 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字", groups = {Create.class, Update.class})
    private String remark;
}
