-- =====================================================================
-- V1012: 多租户 + 组织列全表落地（docs/集团组织与数据权限设计.md v1.1 第 0 章）
-- 目的：
--   * 全部业务表幂等 ADD tenant_id BIGINT NOT NULL DEFAULT 1000（存量归默认租户）；
--   * 隔离表 ADD org_id BIGINT NOT NULL DEFAULT 1（业务数据归总厂=1）；
--     共享表（物料/客商/科目/BOM/菜单/字典/流程定义/工厂等）org_id DEFAULT 0；
--     租户内无组织语义的系统/流程运行时表 org_id 也按 0（列仅为 BaseEntity 映射兼容）；
--   * sys_user 另加 data_scope 数据范围列，并把 uk_username 调整为 (username, tenant_id)
--     —— 租户间允许同名账号，租户内仍唯一。业务单据号（PO/SO..Redis 流水）自带全局
--     唯一性，唯一键不动；共享主数据编码 uk 全局唯一，不动。
-- 幂等性：所有 ALTER 以 information_schema 判断列/索引是否已存在，重复执行安全。
-- 注意：BaseEntity 已映射 tenant_id/org_id，本脚本必须先于新代码启动执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. sys_user：tenant_id / org_id / data_scope + 唯一键调整
-- ---------------------------------------------------------------------

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''主归属组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'data_scope') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `data_scope` VARCHAR(20) NOT NULL DEFAULT ''ALL'' COMMENT ''数据范围:ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF'' AFTER `org_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 唯一键重建：仅当 uk_username 存在且尚未包含 tenant_id 时执行（幂等）
SET @uk_exists := (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_username');
SET @uk_has_tenant := (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_username' AND COLUMN_NAME = 'tenant_id');
SET @ddl := IF(@uk_exists = 1 AND @uk_has_tenant = 0,
    'ALTER TABLE `sys_user` DROP INDEX `uk_username`, ADD UNIQUE KEY `uk_username` (`username`, `tenant_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 其余业务表：tenant_id + org_id（逐表 information_schema 判断，幂等）
--    隔离表 org_id DEFAULT 1；共享/无组织语义表 org_id DEFAULT 0
-- ---------------------------------------------------------------------

-- ast_asset
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_asset' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `ast_asset` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_asset' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `ast_asset` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ast_category
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_category' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `ast_category` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_category' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `ast_category` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ast_depreciation
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_depreciation' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `ast_depreciation` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_depreciation' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `ast_depreciation` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ast_depreciation_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_depreciation_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `ast_depreciation_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ast_depreciation_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `ast_depreciation_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_customer
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_customer' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_customer` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_customer' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_customer` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_location
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_location' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_location` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_location' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_location` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_material
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_material' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_material` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_material' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_material` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_material_category
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_material_category' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_material_category` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_material_category' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_material_category` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_settle_type
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_settle_type' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_settle_type` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_settle_type' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_settle_type` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_supplier
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_supplier' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_supplier` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_supplier' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_supplier` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_unit
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_unit' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_unit` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_unit' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_unit` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- base_warehouse
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_warehouse' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `base_warehouse` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_warehouse' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `base_warehouse` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- cost_flow
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cost_flow' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `cost_flow` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cost_flow' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `cost_flow` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- cost_period
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cost_period' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `cost_period` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cost_period' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `cost_period` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- cost_work_order
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cost_work_order' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `cost_work_order` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cost_work_order' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `cost_work_order` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- eqp_device
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_device' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `eqp_device` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_device' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `eqp_device` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- eqp_maintain_plan
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_maintain_plan' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `eqp_maintain_plan` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_maintain_plan' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `eqp_maintain_plan` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- eqp_maintain_record
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_maintain_record' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `eqp_maintain_record` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_maintain_record' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `eqp_maintain_record` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- eqp_repair
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_repair' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `eqp_repair` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_repair' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `eqp_repair` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- eqp_repair_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_repair_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `eqp_repair_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eqp_repair_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `eqp_repair_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_account
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_account` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_account` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_account_flow
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account_flow' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_account_flow` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account_flow' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_account_flow` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_payable
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payable' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_payable` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payable' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_payable` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_payment
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_payment` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_payment` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_payment_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_payment_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_payment_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_receipt
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_receipt` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_receipt` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_receipt_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_receipt_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_receipt_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_receivable
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receivable' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_receivable` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receivable' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_receivable` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_subject
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_subject' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_subject` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_subject' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_subject` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_voucher
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_voucher` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_voucher` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fin_voucher_entry
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher_entry' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_voucher_entry` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher_entry' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_voucher_entry` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- hr_attendance
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_attendance' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `hr_attendance` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_attendance' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `hr_attendance` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- hr_employee
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_employee' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `hr_employee` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_employee' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `hr_employee` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- hr_piece_price
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_piece_price' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `hr_piece_price` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_piece_price' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `hr_piece_price` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- hr_piece_wage
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_piece_wage' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `hr_piece_wage` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_piece_wage' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `hr_piece_wage` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- hr_piece_wage_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_piece_wage_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `hr_piece_wage_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_piece_wage_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `hr_piece_wage_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_check
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_check` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_check` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_check_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_check_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_check_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_movement
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_movement' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_movement` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_movement' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_movement` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_other_in
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_in' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_other_in` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_in' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_other_in` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_other_in_line
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_in_line' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_other_in_line` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_in_line' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_other_in_line` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_other_out
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_out' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_other_out` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_out' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_other_out` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_other_out_line
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_out_line' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_other_out_line` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_out_line' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_other_out_line` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_purchase_invoice
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_invoice' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_purchase_invoice` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_invoice' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_purchase_invoice` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_sales_invoice
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_sales_invoice' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_sales_invoice` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_sales_invoice' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_sales_invoice` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- inv_stock
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `inv_stock` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `inv_stock` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_bom
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_bom' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_bom` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_bom' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_bom` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_bom_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_bom_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_bom_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_bom_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_bom_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_calc
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_calc' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_calc` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_calc' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_calc` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_calc_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_calc_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_calc_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_calc_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_calc_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_material_issue
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_material_issue' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_material_issue` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_material_issue' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_material_issue` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_material_issue_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_material_issue_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_material_issue_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_material_issue_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_material_issue_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_mps
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_mps' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_mps` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_mps' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_mps` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_operation
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_operation' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_operation` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_operation' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_operation` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_outsource
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_outsource' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_outsource` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_outsource' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_outsource` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_outsource_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_outsource_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_outsource_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_outsource_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_outsource_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_routing
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_routing' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_routing` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_routing' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_routing` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_routing_step
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_routing_step' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_routing_step` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_routing_step' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_routing_step` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_suggestion
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_suggestion' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_suggestion` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_suggestion' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_suggestion` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_wo_step
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_wo_step' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_wo_step` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_wo_step' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_wo_step` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_work_center
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_center' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_work_center` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_center' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_work_center` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_work_order
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_order' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_work_order` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_order' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_work_order` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mrp_work_report
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_report' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `mrp_work_report` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_report' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `mrp_work_report` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- org_factory
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_factory' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `org_factory` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_factory' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `org_factory` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- org_transfer
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_transfer' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `org_transfer` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_transfer' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `org_transfer` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- org_transfer_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_transfer_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `org_transfer_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_transfer_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `org_transfer_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- org_transfer_price
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_transfer_price' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `org_transfer_price` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_transfer_price' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `org_transfer_price` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pur_instock
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_instock' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `pur_instock` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_instock' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `pur_instock` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pur_instock_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_instock_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `pur_instock_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_instock_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `pur_instock_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pur_order
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_order' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `pur_order` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_order' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `pur_order` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pur_order_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_order_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `pur_order_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_order_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `pur_order_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pur_return
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_return' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `pur_return` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_return' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `pur_return` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pur_return_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_return_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `pur_return_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_return_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `pur_return_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- qc_inspect
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_inspect' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `qc_inspect` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_inspect' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `qc_inspect` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- qc_inspect_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_inspect_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `qc_inspect_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_inspect_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `qc_inspect_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- qc_reject
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_reject' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `qc_reject` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_reject' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `qc_reject` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- qc_template
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_template' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `qc_template` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_template' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `qc_template` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- qc_template_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_template_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `qc_template_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_template_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `qc_template_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sal_order
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_order' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sal_order` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_order' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sal_order` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sal_order_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_order_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sal_order_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_order_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sal_order_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sal_outstock
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_outstock' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sal_outstock` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_outstock' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sal_outstock` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sal_outstock_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_outstock_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sal_outstock_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_outstock_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sal_outstock_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sal_return
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_return' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sal_return` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_return' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sal_return` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sal_return_detail
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_return_detail' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sal_return_detail` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_return_detail' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sal_return_detail` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_dept
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_dept` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_dept` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_dict_data
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_dict_data` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_dict_data` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_dict_type
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_type' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_dict_type` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_type' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_dict_type` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_menu
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_menu' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_menu` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_menu' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_menu` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_oper_log
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_oper_log` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_oper_log` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_role
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_role` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_role` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_role_menu
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_menu' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_role_menu` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_menu' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_role_menu` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_user_role
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- wf_definition
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_definition' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `wf_definition` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_definition' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `wf_definition` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- wf_instance
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `wf_instance` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `wf_instance` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- wf_message
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_message' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `wf_message` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_message' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `wf_message` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- wf_node
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_node' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `wf_node` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_node' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `wf_node` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- wf_task
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_task' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `wf_task` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_task' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `wf_task` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=集团共享)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
