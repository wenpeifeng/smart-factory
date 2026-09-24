-- =====================================================================
-- V502: 财务多账套（对标金蝶多账套）
-- 目的：
--   1) 新建账套档案表 fin_book（编码 BS+流水/名称/会计期间政策/本位币/状态/备注），
--      种子：1001 主账套（存量数据全部归它，默认账套不可删）+ 1002 测试账套（空）；
--   2) 全部 11 张 fin 业务表幂等增加 book_id BIGINT NOT NULL DEFAULT 1001，
--      存量数据自然归属 1001 主账套；
--   3) 唯一键按账套维度重建：fin_subject uk_code(code) → (book_id, code)，
--      fin_account uk_account_no(account_no) → (book_id, account_no)
--      —— 不同账套各自维护科目树/资金账户，编码可重复。
-- 幂等性：全部 ALTER/唯一键重建均先查 information_schema 再执行，重复执行无害
-- （并行任务可能同时给 fin_* 表加 tenant_id/org_id 列，互不冲突）。
-- 账套切换约定（详见 docs/modules/finance.md 多账套章节）：
--   * 请求头 X-Book-Id 指定当前账套，缺省 1001；
--   * fin_receivable / fin_payable 外部模块（销售/采购）直接 INSERT 时
--     不指定 book_id 则落默认账套 1001，需记账到其他账套时显式携带 book_id 列；
--   * 1002 测试账套初始为空（科目树需通过「复制科目」接口或手工新增建立，
--     复制入口：POST /finance/books/{bookId}/copy-subjects?fromBookId=1001）。
-- 注：tenant_id/org_id 两列与 V1012（全表补列）模板一致，若本脚本先于 V1012
--   执行则 V1012 的幂等判断自动跳过 fin_book；若后执行，V503 会补齐这两列。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 账套档案表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_book`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '账套ID(种子1001/1002,新建自增)',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户,与V1012对齐)',
    `org_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(0=租户级共享档案,与V1012对齐)',
    `code`          VARCHAR(20)  NOT NULL COMMENT '账套编码(BS+4位流水,如BS1001,账套内唯一)',
    `name`          VARCHAR(60)  NOT NULL COMMENT '账套名称(如:主账套/测试账套)',
    `period_policy` VARCHAR(20)  NOT NULL DEFAULT 'NATURAL_MONTH' COMMENT '会计期间政策:NATURAL_MONTH自然月(当前仅此一种,预留按周/自定义)',
    `currency`      VARCHAR(10)  NOT NULL DEFAULT 'CNY' COMMENT '本位币(ISO代码,默认CNY人民币)',
    `is_default`    TINYINT      NOT NULL DEFAULT 0 COMMENT '默认账套:1是(不可删,无X-Book-Id头时的缺省账套) 0否',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_book_code` (`code`),
    KEY `idx_book_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '账套档案表(多账套)';

-- ---------------------------------------------------------------------
-- 2. 种子账套：1001 主账套（默认）+ 1002 测试账套（空）
-- ---------------------------------------------------------------------
INSERT INTO `fin_book` (`id`, `code`, `name`, `period_policy`, `currency`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 1001, 'BS1001', '主账套', 'NATURAL_MONTH', 'CNY', 1, 1, '系统内置默认账套，存量财务数据全部归属本账套', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_book` WHERE `id` = 1001);

INSERT INTO `fin_book` (`id`, `code`, `name`, `period_policy`, `currency`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 1002, 'BS1002', '测试账套', 'NATURAL_MONTH', 'CNY', 0, 1, '测试用途空账套，科目树需复制或手工建立', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_book` WHERE `id` = 1002);

-- ---------------------------------------------------------------------
-- 3. fin 业务表幂等增加 book_id（11 张表，存量数据归 1001）
--    逐表判断列存在再 ALTER，与并行任务互不冲突
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_subject' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_subject ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_account ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account_flow' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_account_flow ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receivable' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_receivable ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payable' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_payable ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_receipt ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt_item' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_receipt_item ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_payment ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment_item' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_payment_item ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_voucher ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher_entry' AND COLUMN_NAME = 'book_id') = 0,
    'ALTER TABLE fin_voucher_entry ADD COLUMN book_id BIGINT NOT NULL DEFAULT 1001 COMMENT ''所属账套ID(fin_book.id)'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 常用查询索引：book_id 单列索引（账套内分页/统计走 book_id 过滤）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_subject' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_subject ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_voucher ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher_entry' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_voucher_entry ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receivable' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_receivable ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payable' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_payable ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_receipt ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_payment ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_account ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account_flow' AND INDEX_NAME = 'idx_book_id') = 0,
    'ALTER TABLE fin_account_flow ADD INDEX idx_book_id (book_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 5. 唯一键按账套重建：
--    fin_subject   uk_code(code)                → (book_id, code)
--    fin_account   uk_account_no(account_no)    → (book_id, account_no)
--    仅当现唯一键仍是旧单列结构时执行（重复执行 / 已重建过则跳过）。
--    存量数据 book_id 全为 1001，重建后约束语义与原来等价。
--    凭证/收付款单号由全局 Redis 计数器生成天然不重，唯一键保持不动。
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_subject' AND INDEX_NAME = 'uk_code')
        = 'code',
    'ALTER TABLE fin_subject DROP INDEX uk_code, ADD UNIQUE KEY uk_code (book_id, code)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_account' AND INDEX_NAME = 'uk_account_no')
        = 'account_no',
    'ALTER TABLE fin_account DROP INDEX uk_account_no, ADD UNIQUE KEY uk_account_no (book_id, account_no)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
