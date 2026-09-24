-- =====================================================================
-- V503: fin_book 补齐多租户/组织列（与 V1012 全表补列对齐）
-- 背景：
--   V502 创建 fin_book 时，V1012（tenant org columns all tables）尚未执行，
--   两脚本并行落库后 fin_book 成为唯一缺 tenant_id / org_id 列的业务表，
--   而 BaseEntity 已映射这两列（SELECT 字段列表必含）导致 fin_book 全部查询报
--   Unknown column 'tenant_id'。
-- 本脚本按 V1012 相同模板幂等补列：
--   * tenant_id BIGINT NOT NULL DEFAULT 1000（租户维度，租户插件自动追加 WHERE）；
--   * org_id    BIGINT NOT NULL DEFAULT 0（账套为租户级共享档案，org 固定 0；
--     种子两行经列默认值自然归 1000 租户 / org 0）。
-- 幂等：列存在判断后再 ALTER，重复执行无害（V502/V1012 之后任意顺序执行均可）。
-- =====================================================================

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_book' AND COLUMN_NAME = 'tenant_id') = 0,
    'ALTER TABLE `fin_book` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1000 COMMENT ''租户ID(默认1000=默认租户)'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_book' AND COLUMN_NAME = 'org_id') = 0,
    'ALTER TABLE `fin_book` ADD COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''组织ID(0=租户级共享档案)'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
