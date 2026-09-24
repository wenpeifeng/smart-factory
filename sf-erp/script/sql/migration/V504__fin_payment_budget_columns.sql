-- =====================================================================
-- V504: fin_payment 补预算控制挂点列（finance ↔ budget 接线）
-- 背景：
--   预算控制（budget 模块）需要把付款金额归属到 部门×费用科目×期间 才能
--   校验/登记执行数；fin_payment 原只有 供应商/账户/金额/日期（见
--   docs/modules/budget.md §5「付款两段式接线约定」）。
--   本次接线在付款单上补两列（均可空 = 老数据兼容：为空则跳过预算校验）：
--   * dept_id   BIGINT NULL —— 费用归属部门(sys_dept.id，预算控制用)；
--   * subject_id BIGINT NULL —— 预算科目(fin_subject.id，可空)。
-- 幂等：列存在判断后再 ALTER，重复执行无害。
-- =====================================================================

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment' AND COLUMN_NAME = 'dept_id') = 0,
    'ALTER TABLE `fin_payment` ADD COLUMN `dept_id` BIGINT NULL COMMENT ''费用归属部门(预算控制用,sys_dept.id;空=跳过预算校验)'' AFTER `remark`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment' AND COLUMN_NAME = 'subject_id') = 0,
    'ALTER TABLE `fin_payment` ADD COLUMN `subject_id` BIGINT NULL COMMENT ''预算科目(可空,fin_subject.id;空=跳过预算校验)'' AFTER `dept_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
