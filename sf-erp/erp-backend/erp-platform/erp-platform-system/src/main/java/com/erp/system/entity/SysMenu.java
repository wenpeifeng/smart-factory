package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单权限实体（表 sys_menu）。
 * <p>
 * 树形三级结构（parent_id 自关联，0 为根）：
 * <ul>
 *   <li>目录（type=1）：只做分组，path 存一级目录名（如 system）；</li>
 *   <li>菜单（type=2）：path 存完整前端路由（不带斜杠，如 system/users），
 *       component 存前端组件路径（相对 pages/，如 system/users/UserList）；</li>
 *   <li>按钮（type=3）：只存 perms 权限标识（如 system:user:add），用于按钮级鉴权。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 上级菜单 ID，0 表示根节点 */
    private Long parentId;

    /** 菜单/按钮名称 */
    private String name;

    /** 类型：1-目录 2-菜单 3-按钮 */
    private Integer type;

    /** 路由地址（完整路径，不带斜杠） */
    private String path;

    /** 前端组件路径（相对 pages/ 目录） */
    private String component;

    /** 权限标识（按钮级，如 system:user:add） */
    private String perms;

    /** 菜单图标（AntD 图标名） */
    private String icon;

    /** 显示顺序，越小越靠前 */
    private Integer sort;

    /** 是否显示：1-显示 0-隐藏（隐藏的菜单仍可访问路由） */
    private Integer visible;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}
