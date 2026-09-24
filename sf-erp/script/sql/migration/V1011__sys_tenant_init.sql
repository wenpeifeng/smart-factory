-- =====================================================================
-- V1011: 租户基座（docs/集团组织与数据权限设计.md v1.1 第 0 章）
-- 目的：
--   1) 新建 sys_tenant 租户登记表（管理门户维护，平台侧跨租户可见）；
--   2) 种子默认租户 1000（存量全部业务数据归属该租户，见 V1012 列默认值）。
-- 说明：
--   * sys_tenant 自身也带 tenant_id/org_id 列 —— 全实体继承 BaseEntity 后
--     所有实体都会映射这两列，租户表本身按"共享表"语义处理
--     （tenant_id 默认 1000 / org_id 默认 0），租户插件对其忽略；
--   * 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_tenant` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键(租户ID,建议与编码一致如1000)',
    `code`        VARCHAR(40)   NOT NULL COMMENT '租户编码(唯一)',
    `name`        VARCHAR(100)  NOT NULL COMMENT '租户名称',
    `contact`     VARCHAR(50)   DEFAULT NULL COMMENT '联系人',
    `phone`       VARCHAR(20)   DEFAULT NULL COMMENT '联系电话',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `expire_time` DATETIME      DEFAULT NULL COMMENT '到期时间(NULL=永久)',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(共享表语义,恒为默认租户)',
    `org_id`      BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(共享语义0)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户登记表(管理门户)';

-- 种子：默认租户 1000（存量数据归属）
INSERT IGNORE INTO `sys_tenant` (`id`, `code`, `name`, `status`, `remark`)
VALUES (1000, 'default', '默认租户', 1, '存量业务数据归属租户，V1011 种子');
