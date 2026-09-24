-- =====================================================================
-- V621: 生产制造三期（T8 补齐）—— 倒冲领料 + BOM 替代料
-- 内容：
--   1. mrp_work_order 补领料方式列 pick_type（DIRECT 直接领料 / BACKFLUSH 倒冲）
--      与倒冲扣料仓 backflush_warehouse_id（空=沿用 warehouse_id）；
--   2. mrp_material_issue 补 issue_type（NORMAL 手工 / BACKFLUSH 倒冲自动生成，
--      倒冲单直接落 AUDITED），mrp_material_issue_item 补 main_material_id
--      （替代料耗用时记录对应主料，主料行自身为 NULL）；
--   3. 新表 mrp_bom_substitute：BOM 子件替代组（主料=BOM 子件行自身，
--      替代料按 priority 升序依次补足，ratio=1 单位主料耗用 ratio 单位替代料）；
--   4. sys_menu 按钮：BOM 替代料维护 mrp:bom:substitute（挂 BOM 管理 261）。
-- 约定：
--   * DDL 全幂等（列存在判断后再 ALTER；表 CREATE IF NOT EXISTS）；
--   * sys_menu 种子不写 org_id/tenant_id（两列由 V1012 添加，本脚本 621 < 1012，
--     全新库按版本序执行时列尚不存在；已有库两列有默认值），固定 ID + INSERT IGNORE；
--   * 倒冲链路库存不足时整单回滚（与 StockService 现有"库存不足报错"策略一致，
--     不允许负库存）。
-- =====================================================================

-- ---------- 1. mrp_work_order 补列 ----------
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_order' AND COLUMN_NAME = 'pick_type') = 0,
    'ALTER TABLE `mrp_work_order` ADD COLUMN `pick_type` VARCHAR(20) NOT NULL DEFAULT ''DIRECT'' COMMENT ''领料方式:DIRECT直接领料 BACKFLUSH倒冲(完工报工时按BOM自动扣料)'' AFTER `warehouse_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_order' AND COLUMN_NAME = 'backflush_warehouse_id') = 0,
    'ALTER TABLE `mrp_work_order` ADD COLUMN `backflush_warehouse_id` BIGINT DEFAULT NULL COMMENT ''倒冲扣料仓库(空=沿用warehouse_id)'' AFTER `pick_type`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 领料单补列 ----------
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_material_issue' AND COLUMN_NAME = 'issue_type') = 0,
    'ALTER TABLE `mrp_material_issue` ADD COLUMN `issue_type` VARCHAR(20) NOT NULL DEFAULT ''NORMAL'' COMMENT ''单据类型:NORMAL手工领料 BACKFLUSH完工倒冲自动生成'' AFTER `warehouse_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_material_issue_item' AND COLUMN_NAME = 'main_material_id') = 0,
    'ALTER TABLE `mrp_material_issue_item` ADD COLUMN `main_material_id` BIGINT DEFAULT NULL COMMENT ''主料物料ID(替代料倒冲行记录对应主料,主料行自身为NULL)'' AFTER `material_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. BOM 替代组表 ----------
CREATE TABLE IF NOT EXISTS `mrp_bom_substitute` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(0=集团共享,BOM属共享主数据,与V1012口径一致)',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `bom_item_id`     BIGINT        NOT NULL COMMENT 'BOM子件行ID(mrp_bom_item.id,主料即该行物料)',
    `sub_material_id` BIGINT        NOT NULL COMMENT '替代料物料ID(base_material.id)',
    `priority`        INT           NOT NULL DEFAULT 1 COMMENT '优先级(1最高,主料不足时按升序依次补足)',
    `ratio`           DECIMAL(18,6) NOT NULL DEFAULT 1 COMMENT '用量换算比(1单位主料=ratio单位替代料)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_item_material` (`bom_item_id`, `sub_material_id`),
    KEY `idx_sub_material` (`sub_material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM子件替代组(主料=BOM子件行,替代料+优先级+换算比)';

-- ---------- 4. sys_menu 按钮种子（不写 org_id/tenant_id，见文件头说明） ----------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(2615, 261, 'BOM替代料维护', 3, NULL, NULL, 'mrp:bom:substitute', NULL, 5, 1, 1);
