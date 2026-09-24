package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色-菜单关联实体（表 sys_role_menu）。
 * <p>
 * 纯关系表：按角色重分配菜单时对旧关联做物理删除（见 SysRoleMenuMapper XML）。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    /** 主键 */
    @TableId
    private Long id;

    /** 角色 ID（sys_role.id） */
    private Long roleId;

    /** 菜单 ID（sys_menu.id） */
    private Long menuId;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
