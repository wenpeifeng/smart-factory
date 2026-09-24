package com.erp.framework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类 —— 所有数据表实体的公共字段。
 * <p>
 * 建表规范：业务表统一包含 id/tenant_id/org_id/create_by/create_time/update_by/update_time/deleted 八列，
 * 审计四列由 {@link com.erp.framework.config.MybatisPlusConfig} 的审计填充器自动写入，
 * tenant_id/org_id 同样自动填充（取 {@link com.erp.framework.context.OrgContextHolder}，
 * 共享表 org_id 固定 0），deleted 为逻辑删除标记（0 在用 / 1 已删），MP 查询自动过滤已删数据。
 * <p>
 * <b>配套约束</b>：本类映射 tenant_id/org_id 后，所有实体表必须具备这两列
 * （V1012 迁移已全表幂等补齐），新增表时建表语句必须包含（tenant 默认 1000 / 共享表 org 0 / 隔离表 org 1）。
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（商业隔离维度，租户插件自动 WHERE tenant_id=?），MP 插入自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 组织 ID（数据权限维度：共享主数据 0 / 业务组织），MP 插入自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long orgId;

    /** 创建人（用户 ID），MP 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间，MP 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人（用户 ID），MP 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间，MP 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0-在用 1-已删，标注 @TableLogic 后 MP 查询自动过滤已删、删除自动转为 UPDATE */
    @TableLogic
    private Integer deleted;
}
