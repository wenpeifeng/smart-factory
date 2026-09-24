package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-角色关联实体（表 sys_user_role）。
 * <p>
 * 纯关系表：重分配角色时对旧关联做物理删除（见 SysUserRoleMapper XML），
 * 避免逻辑删除累积垃圾行；表仍按建表规范保留审计列。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    /** 主键 */
    @TableId
    private Long id;

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 角色 ID（sys_role.id） */
    private Long roleId;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
