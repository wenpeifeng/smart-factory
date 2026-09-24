package com.mes.system.dto.user;

import com.mes.system.dto.Create;
import com.mes.system.dto.Update;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改表单（Entity 不直接作入参，防 mass assignment）。
 * <p>
 * 差异校验：密码仅新增必填（修改时留空表示不变）。
 */
@Data
public class UserForm {

    /** 用户 ID（修改时由路径参数注入，Controller 在校验后写入，故不做 JSR-303 约束） */
    private Long id;

    /** 登录账号：3-30 位字母/数字/下划线 */
    @NotBlank(message = "账号不能为空", groups = {Create.class, Update.class})
    @Size(min = 3, max = 30, message = "账号长度须在 3-30 位之间", groups = {Create.class, Update.class})
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "账号仅支持字母、数字、下划线", groups = {Create.class, Update.class})
    private String username;

    /** 显示姓名 */
    @NotBlank(message = "姓名不能为空", groups = {Create.class, Update.class})
    @Size(max = 30, message = "姓名最长 30 字", groups = {Create.class, Update.class})
    private String nickname;

    /** 登录密码：新增必填 6-20 位；修改留空表示不修改 */
    @NotBlank(message = "密码不能为空", groups = Create.class)
    @Size(min = 6, max = 20, message = "密码长度须在 6-20 位之间", groups = {Create.class, Update.class})
    private String password;

    /** 手机号（可选） */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确", groups = {Create.class, Update.class})
    private String phone;

    /** 邮箱（可选） */
    @Email(message = "邮箱格式不正确", groups = {Create.class, Update.class})
    @Size(max = 50, message = "邮箱最长 50 字符", groups = {Create.class, Update.class})
    private String email;

    /** 所属部门 ID（可选） */
    private Long deptId;

    /** 租户 ID（可选；缺省由填充器按当前上下文写入，默认租户 1000） */
    private Long tenantId;

    /** 主归属组织 ID（可选；缺省 1=总厂） */
    private Long orgId;

    /** 数据范围（可选：ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF；缺省 ALL） */
    @Pattern(regexp = "^$|^(ALL|ORG_AND_CHILD|ORG_ONLY|DEPT_ONLY|SELF)$",
            message = "数据范围仅支持 ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF",
            groups = {Create.class, Update.class})
    private String dataScope;

    /** 状态：1-启用 0-停用；null 时服务端按启用处理 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字", groups = {Create.class, Update.class})
    private String remark;

    /** 角色 ID 列表（分配角色） */
    private List<Long> roleIds;
}
