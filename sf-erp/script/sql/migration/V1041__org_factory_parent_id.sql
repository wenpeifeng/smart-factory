-- =====================================================================
-- V1041: 组织树父子层级（docs/集团组织与数据权限设计.md 第 2/3 章 ORG_AND_CHILD 档）
-- 目的：
--   1) org_factory 幂等 ADD parent_id BIGINT NULL（NULL = 根组织，总厂 id=1 为根）；
--   2) 种子层级：苏州/东莞工厂挂到总厂（id=1）下，按名称匹配、幂等；
--   3) 辅助索引 idx_factory_parent（OrgTreeProvider 递归收集子组织用）。
-- 说明：
--   * 组织模型仍为 org_factory 单表（完整 org_org 集团树见设计文档第 2 章，未落地），
--     parent_id 使其具备父子层级 —— DataScopeInnerInterceptor 的 ORG_AND_CHILD 档
--     据此注入 org_id IN (本组织及递归子组织)；ORG_ONLY/其余档位不受影响；
--   * 递归深度上限 10 + Redis 短 TTL 兜底见 OrgTreeProviderImpl（组织层级由
--     erp-org 模块维护，无主动失效钩子，靠 10 分钟 TTL 自然收敛）；
--   * 幂等：列/索引以 information_schema 判断存在；种子 UPDATE 带
--     parent_id IS NULL 守卫，不覆盖运维后续调整的层级。
-- =====================================================================

-- ---------- 1. parent_id 列（幂等 ALTER） ----------
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_factory' AND COLUMN_NAME = 'parent_id') = 0,
    'ALTER TABLE `org_factory` ADD COLUMN `parent_id` BIGINT NULL COMMENT ''上级组织ID(org_factory.id,NULL=根,总厂id=1)'' AFTER `org_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 父组织索引（幂等） ----------
SET @ddl := IF(
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_factory' AND INDEX_NAME = 'idx_factory_parent') = 0,
    'ALTER TABLE `org_factory` ADD KEY `idx_factory_parent` (`parent_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 根组织：总厂 parent_id 置 NULL（幂等，仅纠正误设） ----------
UPDATE `org_factory` SET `parent_id` = NULL WHERE `id` = 1 AND `parent_id` IS NOT NULL;

-- ---------- 4. 种子层级：苏州/东莞 → 总厂(1)（按名称匹配 + 未设父守卫，幂等） ----------
UPDATE `org_factory` SET `parent_id` = 1
WHERE `parent_id` IS NULL AND `id` <> 1 AND `deleted` = 0 AND `name` LIKE '苏州%';
UPDATE `org_factory` SET `parent_id` = 1
WHERE `parent_id` IS NULL AND `id` <> 1 AND `deleted` = 0 AND `name` LIKE '东莞%';
