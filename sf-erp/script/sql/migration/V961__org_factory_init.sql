-- =====================================================================
-- V961 组织管理(erp-org)初始化之一：工厂档案 + 仓库归属 + 调拨价目
-- 目的：轻量多组织（多工厂）落地——
--   1) org_factory        工厂档案（编码 FC+yyyyMMdd+3位流水，种子 id=1 总厂）
--   2) base_warehouse     + factory_id 所属工厂列（幂等：information_schema
--                          判断列存在则跳过；存量仓库统一归 1=总厂）
--   3) org_transfer_price 组织间调拨价目（物料×调出工厂×调入工厂 唯一）
-- 跨模块约定：base_warehouse 为 basedata 模块标准表，本脚本只加列/回填，
-- 不改其唯一键与其余结构；erp-org 模块按约定列读写，不 import 对方代码。
-- =====================================================================

-- ---------- 1. 工厂档案 ----------
CREATE TABLE IF NOT EXISTS `org_factory`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(40)  NOT NULL COMMENT '工厂编码(唯一,自动生成:FC+yyyyMMdd+3位流水)',
    `name`        VARCHAR(100) NOT NULL COMMENT '工厂名称',
    `address`     VARCHAR(200) DEFAULT NULL COMMENT '工厂地址',
    `manager`     VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_factory_code` (`code`),
    KEY `idx_factory_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工厂档案(轻量多组织)';

-- ---------- 2. base_warehouse 增加所属工厂列（幂等 ALTER） ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_warehouse' AND COLUMN_NAME = 'factory_id') = 0,
    'ALTER TABLE base_warehouse ADD COLUMN factory_id BIGINT NULL COMMENT ''所属工厂ID(org_factory.id,存量仓库默认归总厂)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 种子：总厂（id=1） ----------
INSERT INTO org_factory (id, code, name, address, manager, status, remark, create_by, update_by)
SELECT 1, 'FC0001', '总厂', NULL, NULL, 1, '系统内置默认工厂，存量仓库统一归属', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM org_factory WHERE id = 1);

-- ---------- 4. 存量仓库归属回填（无归属 → 总厂；可重复执行） ----------
UPDATE base_warehouse SET factory_id = 1 WHERE factory_id IS NULL;

-- ---------- 5. 组织间调拨价目 ----------
CREATE TABLE IF NOT EXISTS `org_transfer_price`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id`    BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `from_factory_id` BIGINT       NOT NULL COMMENT '调出工厂ID(org_factory.id)',
    `to_factory_id`  BIGINT        NOT NULL COMMENT '调入工厂ID(org_factory.id)',
    `markup_type`    VARCHAR(10)   NOT NULL COMMENT '加价类型:RATE加价率% FIXED固定加价额',
    `markup_value`   DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '加价值:RATE=百分数(10代表10%) FIXED=每单位加价金额',
    `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`         VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_price_line` (`material_id`, `from_factory_id`, `to_factory_id`, `deleted`),
    KEY `idx_tprice_factory` (`from_factory_id`, `to_factory_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '组织间调拨价目(物料×调出工厂×调入工厂)';
