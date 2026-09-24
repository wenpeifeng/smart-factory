-- =====================================================================
-- V972: 预算管理模块补齐多租户/组织列（erp-biz-budget，版本段 V97x）
-- 背景：V1011/V1012 引入多租户与组织架构 —— BaseEntity 已映射 tenant_id/org_id，
--       租户插件(TenantLineInnerInterceptor)对未列入共享白名单的表自动追加
--       WHERE tenant_id=? 且 INSERT 自动补租户列；预算 4 表为 V971 新建，
--       未在 V1012 的存量表清单内，必须在本模块版本段内补齐两列，
--       否则所有 budget_* 查询/插入报 Unknown column 'tenant_id'。
-- 口径：预算方案/明细/配置/执行数均为【租户隔离 + 组织隔离】业务数据：
--       tenant_id BIGINT NOT NULL DEFAULT 1000（存量归默认租户）；
--       org_id    BIGINT NOT NULL DEFAULT 1（存量归 1=总厂，与 fin_/pur_ 等单据表一致）。
-- 幂等：逐表 information_schema 判断列存在则跳过（与 V1012 同款写法）。
-- =====================================================================

-- budget_plan
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_plan' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `budget_plan` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_plan' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `budget_plan` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- budget_item
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_item' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `budget_item` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_item' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `budget_item` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- budget_control_config
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_control_config' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `budget_control_config` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_control_config' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `budget_control_config` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- budget_usage_record
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_usage_record' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `budget_usage_record` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budget_usage_record' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `budget_usage_record` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''组织ID(org_factory.id,1=总厂)'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
