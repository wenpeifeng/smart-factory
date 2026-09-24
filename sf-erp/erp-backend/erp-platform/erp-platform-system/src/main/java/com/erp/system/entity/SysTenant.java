package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户实体（表 sys_tenant，管理门户维护）。
 * <p>
 * 租户是商业隔离边界（tenant_id），组织是权限边界（org_id），两者同时具备。
 * 本表在租户插件白名单内（平台跨租户可见）；tenantId/orgId 列仅为 BaseEntity
 * 映射兼容（填充固定 1000 / 0），不参与隔离语义。
 * <p>
 * 状态：1-启用 0-停用；expireTime 为空表示永久有效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    /** 租户编码（唯一，如 default / t2001） */
    private String code;

    /** 租户名称 */
    private String name;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 到期时间（NULL=永久） */
    private LocalDateTime expireTime;

    /** 备注 */
    private String remark;
}
