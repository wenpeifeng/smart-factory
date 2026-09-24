package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体（表 sys_user）。
 * <p>
 * 口令仅存 BCrypt 密文；状态：1-启用 0-停用。
 * 多租户/组织（v1.1）：tenantId 继承自 BaseEntity（唯一键为
 * username+tenant_id，租户间允许同名账号）；orgId 为主归属组织、
 * dataScope 为数据范围五档枚举（ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 登录账号（租户内唯一，uk_username = username + tenant_id） */
    private String username;

    /** 工号（auth-center 身份键，不可变；同步/JIT/SSO 匹配键，uk_employee_no） */
    private String employeeNo;

    /** 显示姓名 */
    private String nickname;

    /** BCrypt 加密口令 */
    private String password;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 所属部门 ID（sys_dept.id） */
    private Long deptId;

    /** 主归属组织 ID（org_factory.id，默认 1=总厂） */
    private Long orgId;

    /** 数据范围：ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF（见 DataScope 枚举） */
    private String dataScope;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
