package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-组织绑定实体（表 sys_user_org，V1042）。
 * <p>
 * 登录/切换组织的"可切换组织列表"数据源：有绑定的用户仅见绑定的启用组织，
 * 无绑定（含超管/存量用户）回退全部启用组织。
 * 纯关系表：重分配为"整体覆盖"语义（物理删除旧绑定再插入，见
 * SysUserOrgMapper XML），避免逻辑删除在 uk(user_id, org_id) 上撞键累积垃圾行。
 * 表带 tenant_id（租户插件自动追加条件/补列）；org_id 即绑定目标组织列。
 */
@Data
@TableName("sys_user_org")
public class SysUserOrg implements Serializable {

    /** 主键 */
    @TableId
    private Long id;

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 绑定组织 ID（org_factory.id） */
    private Long orgId;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
