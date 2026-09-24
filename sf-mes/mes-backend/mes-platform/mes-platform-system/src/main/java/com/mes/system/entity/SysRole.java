package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体（表 sys_role）。
 * <p>
 * RBAC 权限模型的枢纽：用户通过 sys_user_role 关联角色，角色通过
 * sys_role_menu 关联菜单/按钮。code 为 super_admin 的角色是内置超管，
 * 服务层直接授予全部菜单，无需逐条维护关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称（展示用，如"采购经理"） */
    private String name;

    /** 角色编码，全局唯一；内置超管编码：super_admin */
    private String code;

    /** 显示顺序，越小越靠前 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
