-- =====================================================================
-- V1043: sys_user 增加 employee_no 列（统一认证中心 sf-auth 接入）
-- 目的：
--   1) 认证中心令牌 claim employee_no（工号，不可变）作为 SSO 登录匹配
--      本地用户的唯一键（docs/modules/sso-authcenter.md）；
--   2) 用户同步（SCIM 对账）按 employee_no upsert：新增用户
--      username=employee_no、随机密码不可本地登录；active=false 软禁用；
--   3) 按钮菜单种子：统一认证手动同步复用 system:user:edit 既有权限码（免新码）。
-- 说明：
--   * 唯一索引 uk_employee_no 允许 NULL（存量本地账号多行 NULL 不冲突）；
--   * 存量演示用户按需回填：zhanggm→E1001、wangcg→E1002，其余留空；
--   * 幂等：information_schema 判断列/索引存在才执行（Flyway out-of-order 已开启）。
-- =====================================================================

-- 1) 加列（幂等，PREPARE 动态 DDL）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'employee_no');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `employee_no` VARCHAR(64) DEFAULT NULL COMMENT ''工号(统一认证中心匹配键)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 唯一索引（幂等，允许 NULL 多行共存）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_employee_no');
SET @ddl := IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_employee_no` ON `sys_user` (`employee_no`)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 存量演示用户回填工号（E1001/E1002 之外的留空，重复执行无副作用）
UPDATE `sys_user` SET `employee_no` = 'E1001' WHERE `username` = 'zhanggm' AND `deleted` = 0 AND `employee_no` IS NULL;
UPDATE `sys_user` SET `employee_no` = 'E1002' WHERE `username` = 'wangcg' AND `deleted` = 0 AND `employee_no` IS NULL;

-- 4) 菜单种子（sys_menu 规范：不写 org_id/tenant_id 列；INSERT IGNORE 幂等）
--    "系统管理-用户管理"（id=11）下补按钮：手动同步认证中心用户（perms 复用 system:user:edit）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(119, 11, '同步认证中心用户', 3, NULL, NULL, 'system:user:edit', NULL, 9, 1, 1);
