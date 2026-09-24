-- =====================================================================
-- V501: 财务管理模块初始化（erp-finance）
-- 目的：
--   1) 建立总账/应收应付/资金账户 全套表结构（11 张表）
--   2) 预置标准会计科目种子数据（企业会计准则常用科目，资产/负债/权益/成本/损益五大类，37 条）
--   3) 预置两个资金账户（库存现金 / 银行基本户），便于收付款核销开箱可用
-- 协作约定：
--   fin_receivable 由销售模块出库后直接 INSERT（customer_id/biz_no/amount/settled_amount/status）；
--   fin_payable  由采购模块入库后直接 INSERT。本模块为两张表的 owner，
--   负责核销回写（settled_amount / status）与查询。客户/供应商名称由
--   查询时关联 base_customer / base_supplier（不 import 对方代码）。
-- 金额规范：全部 DECIMAL(18,2)，代码层 BigDecimal + HALF_UP，禁止 float/double。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 会计科目表（树形：parent_id = 0 表示一级科目）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_subject` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(20)  NOT NULL COMMENT '科目编码(如1001/1122/222101)',
    `name`        VARCHAR(60)  NOT NULL COMMENT '科目名称',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级科目ID(0=一级科目)',
    `category`    VARCHAR(20)  NOT NULL COMMENT '科目类别:ASSET资产 LIABILITY负债 EQUITY权益 COST成本 PROFIT_LOSS损益',
    `direction`   VARCHAR(10)  NOT NULL COMMENT '余额方向:DEBIT借方 CREDIT贷方',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会计科目表';

-- ---------------------------------------------------------------------
-- 2. 资金账户表（现金 / 银行账户）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_account` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_no`      VARCHAR(30)   NOT NULL COMMENT '账户编号(唯一)',
    `name`            VARCHAR(60)   NOT NULL COMMENT '账户名称(如:库存现金/工行基本户)',
    `type`            VARCHAR(10)   NOT NULL COMMENT '账户类型:CASH现金 BANK银行',
    `bank_name`       VARCHAR(60)   DEFAULT NULL COMMENT '开户银行(银行账户必填)',
    `bank_account_no` VARCHAR(50)   DEFAULT NULL COMMENT '银行账号',
    `initial_balance` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '期初余额',
    `current_balance` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '当前余额(收付款原子累加)',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_no` (`account_no`),
    KEY `idx_type` (`type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资金账户表';

-- ---------------------------------------------------------------------
-- 3. 账户流水表（每笔收支自动记录，含记账后余额快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_account_flow` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_id`    BIGINT        NOT NULL COMMENT '资金账户ID(fin_account.id)',
    `biz_date`      DATE          NOT NULL COMMENT '业务日期',
    `biz_type`      VARCHAR(20)   NOT NULL COMMENT '业务类型:RECEIPT收款单 PAYMENT付款单',
    `biz_id`        BIGINT        DEFAULT NULL COMMENT '业务单据ID(收/付款单主键)',
    `biz_no`        VARCHAR(40)   DEFAULT NULL COMMENT '业务单号(SK/FK开头)',
    `direction`     VARCHAR(10)   NOT NULL COMMENT '收支方向:INCOME收入 EXPENSE支出',
    `amount`        DECIMAL(18,2) NOT NULL COMMENT '发生金额(恒为正数)',
    `balance_after` DECIMAL(18,2) NOT NULL COMMENT '记账后账户余额快照',
    `remark`        VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '账户流水表';

-- ---------------------------------------------------------------------
-- 4. 应收单表（销售出库生成；本模块负责核销回写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_receivable` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`    BIGINT        NOT NULL COMMENT '客户ID(base_customer.id)',
    `biz_no`         VARCHAR(40)   NOT NULL COMMENT '来源业务单号(销售出库单号)',
    `biz_date`       DATE          DEFAULT NULL COMMENT '业务日期(销售模块可空,手工录入必填)',
    `due_date`       DATE          DEFAULT NULL COMMENT '到期日',
    `amount`         DECIMAL(18,2) NOT NULL COMMENT '应收金额',
    `settled_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已核销金额(收款核销原子累加)',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'UNSETTLED' COMMENT '状态:UNSETTLED未核销 PARTIAL部分核销 SETTLED已核销',
    `remark`         VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_status` (`status`),
    KEY `idx_biz_no` (`biz_no`),
    KEY `idx_biz_date` (`biz_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '应收单表';

-- ---------------------------------------------------------------------
-- 5. 应付单表（采购入库生成；本模块负责核销回写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_payable` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `supplier_id`    BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `biz_no`         VARCHAR(40)   NOT NULL COMMENT '来源业务单号(采购入库单号)',
    `biz_date`       DATE          DEFAULT NULL COMMENT '业务日期(采购模块可空,手工录入必填)',
    `due_date`       DATE          DEFAULT NULL COMMENT '到期日',
    `amount`         DECIMAL(18,2) NOT NULL COMMENT '应付金额',
    `settled_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已核销金额(付款核销原子累加)',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'UNSETTLED' COMMENT '状态:UNSETTLED未核销 PARTIAL部分核销 SETTLED已核销',
    `remark`         VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_biz_no` (`biz_no`),
    KEY `idx_biz_date` (`biz_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '应付单表';

-- ---------------------------------------------------------------------
-- 6. 收款单主表（核销应收）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_receipt` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `receipt_no`      VARCHAR(40)   NOT NULL COMMENT '收款单号(SK+日期+流水,如SK202609050001)',
    `customer_id`     BIGINT        NOT NULL COMMENT '客户ID',
    `account_id`      BIGINT        NOT NULL COMMENT '收款资金账户ID',
    `biz_date`        DATE          NOT NULL COMMENT '收款日期',
    `amount`          DECIMAL(18,2) NOT NULL COMMENT '收款金额',
    `verified_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已核销金额(当前模型下=收款金额)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_receipt_no` (`receipt_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_biz_date` (`biz_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收款单主表';

-- ---------------------------------------------------------------------
-- 7. 收款核销明细表（每笔应收的本次核销额）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_receipt_item` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `receipt_id`     BIGINT        NOT NULL COMMENT '收款单ID(fin_receipt.id)',
    `receivable_id`  BIGINT        NOT NULL COMMENT '应收单ID(fin_receivable.id)',
    `verify_amount`  DECIMAL(18,2) NOT NULL COMMENT '本次核销金额',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_receipt_id` (`receipt_id`),
    KEY `idx_receivable_id` (`receivable_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收款核销明细表';

-- ---------------------------------------------------------------------
-- 8. 付款单主表（核销应付）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_payment` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `payment_no`      VARCHAR(40)   NOT NULL COMMENT '付款单号(FK+日期+流水,如FK202609050001)',
    `supplier_id`     BIGINT        NOT NULL COMMENT '供应商ID',
    `account_id`      BIGINT        NOT NULL COMMENT '付款资金账户ID',
    `biz_date`        DATE          NOT NULL COMMENT '付款日期',
    `amount`          DECIMAL(18,2) NOT NULL COMMENT '付款金额',
    `verified_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已核销金额(当前模型下=付款金额)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_biz_date` (`biz_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '付款单主表';

-- ---------------------------------------------------------------------
-- 9. 付款核销明细表（每笔应付的本次核销额）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_payment_item` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `payment_id`    BIGINT        NOT NULL COMMENT '付款单ID(fin_payment.id)',
    `payable_id`    BIGINT        NOT NULL COMMENT '应付单ID(fin_payable.id)',
    `verify_amount` DECIMAL(18,2) NOT NULL COMMENT '本次核销金额',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_payment_id` (`payment_id`),
    KEY `idx_payable_id` (`payable_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '付款核销明细表';

-- ---------------------------------------------------------------------
-- 10. 记账凭证主表（借贷平衡，审核后过账）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_voucher` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `voucher_no`       VARCHAR(40)   NOT NULL COMMENT '凭证号(PZ+日期+流水,如PZ202609050001)',
    `voucher_date`     DATE          NOT NULL COMMENT '凭证日期(记账所属期间)',
    `word`             VARCHAR(10)   NOT NULL DEFAULT '记' COMMENT '凭证字:记/收/付/转',
    `attachment_count` INT           NOT NULL DEFAULT 0 COMMENT '附件张数',
    `total_debit`      DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '借方金额合计',
    `total_credit`     DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '贷方金额合计',
    `status`           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 POSTED已过账(过账后禁改禁删)',
    `audit_by`         BIGINT        DEFAULT NULL COMMENT '审核人',
    `audit_time`       DATETIME      DEFAULT NULL COMMENT '审核时间',
    `remark`           VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`        BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voucher_no` (`voucher_no`),
    KEY `idx_voucher_date` (`voucher_date`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '记账凭证主表';

-- ---------------------------------------------------------------------
-- 11. 凭证分录表（每行：摘要 + 科目 + 借/贷单边金额）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_voucher_entry` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `voucher_id`    BIGINT        NOT NULL COMMENT '凭证ID(fin_voucher.id)',
    `line_no`       INT           NOT NULL COMMENT '分录行号(从1开始)',
    `summary`       VARCHAR(200)  NOT NULL COMMENT '摘要',
    `subject_id`    BIGINT        NOT NULL COMMENT '科目ID(fin_subject.id,须为末级科目)',
    `debit_amount`  DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '借方金额(与贷方互斥,仅一侧>0)',
    `credit_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '贷方金额(与借方互斥,仅一侧>0)',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_voucher_id` (`voucher_id`),
    KEY `idx_subject_id` (`subject_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '凭证分录表';

-- ---------------------------------------------------------------------
-- 种子数据 1：标准会计科目（企业会计准则常用科目，37 条）
-- 类别：ASSET资产 LIABILITY负债 EQUITY权益 COST成本 PROFIT_LOSS损益
-- 方向：DEBIT借方 CREDIT贷方
-- ---------------------------------------------------------------------
INSERT INTO `fin_subject` (`id`, `code`, `name`, `parent_id`, `category`, `direction`, `status`, `remark`) VALUES
(1,  '1001',   '库存现金',     0,  'ASSET',      'DEBIT',  1, '资产类-货币资金'),
(2,  '1002',   '银行存款',     0,  'ASSET',      'DEBIT',  1, '资产类-货币资金'),
(3,  '1012',   '其他货币资金', 0,  'ASSET',      'DEBIT',  1, '资产类-货币资金'),
(4,  '1122',   '应收账款',     0,  'ASSET',      'DEBIT',  1, '销售出库自动挂账科目'),
(5,  '1123',   '预付账款',     0,  'ASSET',      'DEBIT',  1, '资产类-应收预付'),
(6,  '1221',   '其他应收款',   0,  'ASSET',      'DEBIT',  1, '资产类-应收预付'),
(7,  '1231',   '坏账准备',     0,  'ASSET',      'CREDIT', 1, '应收账款备抵科目(贷方余额)'),
(8,  '1403',   '原材料',       0,  'ASSET',      'DEBIT',  1, '资产类-存货'),
(9,  '1405',   '库存商品',     0,  'ASSET',      'DEBIT',  1, '资产类-存货'),
(10, '1601',   '固定资产',     0,  'ASSET',      'DEBIT',  1, '资产类-长期资产'),
(11, '1602',   '累计折旧',     0,  'ASSET',      'CREDIT', 1, '固定资产备抵科目(贷方余额)'),
(12, '2001',   '短期借款',     0,  'LIABILITY',  'CREDIT', 1, '负债类-借款'),
(13, '2202',   '应付账款',     0,  'LIABILITY',  'CREDIT', 1, '采购入库自动挂账科目'),
(14, '2203',   '预收账款',     0,  'LIABILITY',  'CREDIT', 1, '负债类-应付预收'),
(15, '2211',   '应付职工薪酬', 0,  'LIABILITY',  'CREDIT', 1, '负债类-应付职工薪酬'),
(16, '2221',   '应交税费',     0,  'LIABILITY',  'CREDIT', 1, '负债类-应交税费'),
(17, '222101', '应交增值税',   16, 'LIABILITY',  'CREDIT', 1, '应交税费-明细'),
(18, '2501',   '长期借款',     0,  'LIABILITY',  'CREDIT', 1, '负债类-借款'),
(19, '4001',   '实收资本',     0,  'EQUITY',     'CREDIT', 1, '权益类'),
(20, '4002',   '资本公积',     0,  'EQUITY',     'CREDIT', 1, '权益类'),
(21, '4103',   '本年利润',     0,  'EQUITY',     'CREDIT', 1, '权益类'),
(22, '4104',   '利润分配',     0,  'EQUITY',     'CREDIT', 1, '权益类'),
(23, '5001',   '生产成本',     0,  'COST',       'DEBIT',  1, '成本类'),
(24, '500101', '直接材料',     23, 'COST',       'DEBIT',  1, '生产成本-明细'),
(25, '500102', '直接人工',     23, 'COST',       'DEBIT',  1, '生产成本-明细'),
(26, '5101',   '制造费用',     0,  'COST',       'DEBIT',  1, '成本类'),
(27, '6001',   '主营业务收入', 0,  'PROFIT_LOSS', 'CREDIT', 1, '损益类-收入'),
(28, '6051',   '其他业务收入', 0,  'PROFIT_LOSS', 'CREDIT', 1, '损益类-收入'),
(29, '6111',   '投资收益',     0,  'PROFIT_LOSS', 'CREDIT', 1, '损益类-收入'),
(30, '6301',   '营业外收入',   0,  'PROFIT_LOSS', 'CREDIT', 1, '损益类-收入'),
(31, '6401',   '主营业务成本', 0,  'PROFIT_LOSS', 'DEBIT',  1, '损益类-费用成本'),
(32, '6402',   '其他业务成本', 0,  'PROFIT_LOSS', 'DEBIT',  1, '损益类-费用成本'),
(33, '6601',   '销售费用',     0,  'PROFIT_LOSS', 'DEBIT',  1, '损益类-期间费用'),
(34, '6602',   '管理费用',     0,  'PROFIT_LOSS', 'DEBIT',  1, '损益类-期间费用'),
(35, '6603',   '财务费用',     0,  'PROFIT_LOSS', 'DEBIT',  1, '损益类-期间费用'),
(36, '6711',   '营业外支出',   0,  'PROFIT_LOSS', 'DEBIT',  1, '损益类'),
(37, '6801',   '所得税费用',   0,  'PROFIT_LOSS', 'DEBIT',  1, '损益类');

-- ---------------------------------------------------------------------
-- 种子数据 2：资金账户（现金 + 银行基本户，开箱即用于收付款核销演示）
-- ---------------------------------------------------------------------
INSERT INTO `fin_account` (`account_no`, `name`, `type`, `bank_name`, `bank_account_no`, `initial_balance`, `current_balance`, `status`, `remark`) VALUES
('CASH-001', '库存现金',    'CASH', NULL,        NULL,              10000.00,  10000.00,  1, '默认现金账户'),
('BANK-001', '工行基本户',  'BANK', '中国工商银行', '6222 0200 0012 3456 789', 500000.00, 500000.00, 1, '默认银行结算账户');
