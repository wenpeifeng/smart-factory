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
