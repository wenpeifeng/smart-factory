-- =====================================================================
-- V16: MES 组织基座（对齐 ERP org_factory 模型，供组织上下文/数据权限使用）
-- 内容：
--   1) org_factory 工厂/组织档案（含 parent_id 层级 + tenant_id/org_id 列，新表规范）；
--   2) sys_user_org 用户-组织绑定表（可切换组织来源）；
--   3) 种子：总厂(1)=苏州精工机械总厂 + 一金工车间组织(2)，admin 绑定总厂。
-- 说明：
--   * 组织档案为共享表语义（org_id=0），MES 与 ERP 集成时组织主数据由
--     integration 模块从 ERP 同步（见 docs/MES与ERP协同设计.md）；
--   * 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `org_factory`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`   BIGINT       NULL COMMENT '上级组织ID(org_factory.id,NULL=根,总厂id=1)',
    `code`        VARCHAR(40)  NOT NULL COMMENT '工厂编码(唯一,自动生成:FC+yyyyMMdd+3位流水)',
    `name`        VARCHAR(100) NOT NULL COMMENT '工厂名称',
    `address`     VARCHAR(200) DEFAULT NULL COMMENT '工厂地址',
    `manager`     VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(共享语义,默认1000)',
    `org_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(共享语义0)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_factory_code` (`code`),
    KEY `idx_factory_status` (`status`),
    KEY `idx_factory_parent` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工厂档案(轻量多组织,MES组织主数据由ERP同步)';

CREATE TABLE IF NOT EXISTS `sys_user_org`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID(sys_user.id)',
    `org_id`      BIGINT       NOT NULL COMMENT '组织ID(org_factory.id,绑定的可切换组织)',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删(绑定行整体覆盖维护,恒为0)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_org` (`user_id`, `org_id`),
    KEY `idx_user_org_org` (`org_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-组织绑定(可切换组织来源,空=全部启用组织)';

-- 种子组织：1=总厂(根) 2=一金工车间（parent=1）
INSERT IGNORE INTO `org_factory` (`id`, `parent_id`, `code`, `name`, `address`, `manager`, `status`)
VALUES (1, NULL, 'FC0001', '苏州精工机械总厂', '江苏省苏州市工业园区金鸡湖大道99号', '张国民', 1),
       (2, 1,    'FC0002', '一金工车间',       '总厂1号厂房',                        '周长顺', 1);

-- admin 绑定总厂
INSERT IGNORE INTO `sys_user_org` (`user_id`, `org_id`) VALUES (1, 1);
