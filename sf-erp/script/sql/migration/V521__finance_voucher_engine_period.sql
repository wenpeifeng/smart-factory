-- =====================================================================
-- V521: 业务财务一体化——凭证模板引擎 + 总账期间结账
-- 内容：
--   1) fin_voucher 幂等补 source_type/source_id 来源列（模板引擎幂等依据）
--      + 唯一键 uk_voucher_source(book_id, source_type, source_id)
--      （手工凭证来源列为 NULL，MySQL 唯一键对 NULL 不去重，互不影响；
--        DB 层兜底同一账套同一业务单据只生成一张自动凭证）；
--   2) 凭证模板表 fin_voucher_template + 八类业务单据标准模板种子；
--   3) 总账会计期间表 fin_period（与 cost_period 成本期间相互独立）；
--   4) 菜单/按钮权限种子：凭证模板(274)、会计期间(275)。
-- 幂等性：全部 ALTER/INSERT 均先查 information_schema / NOT EXISTS 再执行，
-- 重复执行无害（与 V502/V503 同一模板）。
--
-- 【科目金额口径表】（对照各单据表明细汇总口径，模板引擎按此取数）
--  biz_type          | 金额来源字段        | 口径说明
--  ------------------|--------------------|------------------------------------------
--  PUR_INSTOCK       | detail_tax_excl_total | Σ明细 amount÷(1+tax_rate%) HALF_UP 2位
--                    |                    | （pur_instock_detail.price 为含税单价，
--                    |                    |  amount=qty×含税单价，故不含税需价税分离）
--                    | order_total        | total_amount=价税合计（含税）
--                    | tax_total          | 价税合计−不含税合计（倒挤，保证借贷必平）
--  PUR_RET           | 同 PUR_INSTOCK     | pur_return(_detail) 同构，引擎取负数（红字）
--  SAL_OUTSTOCK      | order_total        | total_amount=Σ明细 amount（qty×不含税单价，
--                    |                    |  与发票模块口径一致：销项税=不含税×税率）
--                    | detail_cost_total  | Σ明细 actual_cost（V2101 结转成本快照，
--                    |                    |  NULL=历史行无成本，计 0；全 0 时跳过成本腿）
--  SAL_RET           | order_total        | sal_return.total_amount（正数存储，引擎取负）
--                    | detail_cost_total  | 按退货数量分摊原出库行 actual_cost：
--                    |                    |  round(原行actual_cost×退货qty÷原行qty,2)
--  INVOICE_SALES     | tax_total          | inv_sales_invoice.tax_amount（红字票已为负）
--                    | order_total        | total_amount=价税合计
--                    | detail_tax_excl_total | amount（不含税）
--  INVOICE_PURCHASE  | 同 INVOICE_SALES   | inv_purchase_invoice 同构
--  RECEIPT           | order_total        | fin_receipt.amount（收款金额）
--  PAYMENT           | order_total        | fin_payment.amount（付款金额）
--  @account 特殊科目：按单据 account_id → fin_account.type 解析
--                    | CASH→1001 库存现金 / BANK→1002 银行存款
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. fin_voucher 来源列（幂等）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher' AND COLUMN_NAME = 'source_type') = 0,
    'ALTER TABLE fin_voucher ADD COLUMN source_type VARCHAR(30) DEFAULT NULL COMMENT ''业务来源类型(PUR_INSTOCK/SAL_OUTSTOCK/SAL_RET/PUR_RET/INVOICE_SALES/INVOICE_PURCHASE/RECEIPT/PAYMENT,NULL=手工凭证)'' AFTER remark',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher' AND COLUMN_NAME = 'source_id') = 0,
    'ALTER TABLE fin_voucher ADD COLUMN source_id BIGINT DEFAULT NULL COMMENT ''业务来源单据ID(与source_type联合定位来源单,幂等键成分)'' AFTER source_type',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 唯一键（幂等硬保证）：同账套同来源单据仅一张自动凭证；NULL 来源不受约束
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_voucher' AND INDEX_NAME = 'uk_voucher_source') = 0,
    'ALTER TABLE fin_voucher ADD UNIQUE KEY uk_voucher_source (book_id, source_type, source_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 凭证模板表
--    entry_rules 为分录规则 JSON 数组，每元素：
--    {"side":"DEBIT|CREDIT","subjectCode":"1405|@account","amountExpr":"...","summary":"... {单号} ..."}
--    摘要变量：{单号}=来源单号 {日期}=单据日期 {类型}=业务类型中文名
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_voucher_template`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `tenant_id`  BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `org_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(0=租户级共享档案,模板跨组织通用)',
    `code`       VARCHAR(30)  NOT NULL COMMENT '模板编码(如TPL-PUR-INSTOCK)',
    `name`       VARCHAR(60)  NOT NULL COMMENT '模板名称',
    `biz_type`   VARCHAR(30)  NOT NULL COMMENT '单据类型:PUR_INSTOCK采购入库/SAL_OUTSTOCK销售出库/SAL_RET销售退货/PUR_RET采购退货/INVOICE_SALES销项发票/INVOICE_PURCHASE进项发票/RECEIPT收款/PAYMENT付款',
    `entry_rules` TEXT        NOT NULL COMMENT '借贷分录规则JSON数组[{side,subjectCode,amountExpr,summary}]',
    `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '启用:1是 0否(停用后扫描器不再按该模板生成)',
    `priority`   INT          NOT NULL DEFAULT 100 COMMENT '优先级(预留,当前uk(biz_type)单模板)',
    `remark`     VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`  BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`  BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tpl_biz` (`biz_type`),
    KEY `idx_tpl_enabled` (`enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '凭证模板(业务单据→会计凭证分录规则)';

-- ---------------------------------------------------------------------
-- 3. 八类标准模板种子（科目=现行 fin_subject 种子编码，全部末级）
--    暂估链路：入库按不含税暂估(借1405贷2202)，发票到达补税差
--    (进项 借222101贷2202 / 销项 借1122贷222101)，应付/应收最终=价税合计
-- ---------------------------------------------------------------------
INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-PUR-INSTOCK', '采购入库暂估凭证', 'PUR_INSTOCK',
       '[{"side":"DEBIT","subjectCode":"1405","amountExpr":"detail_tax_excl_total","summary":"采购入库 {单号} 暂估入库存商品"},{"side":"CREDIT","subjectCode":"2202","amountExpr":"detail_tax_excl_total","summary":"采购入库 {单号} 暂估应付"}]',
       1, 100, '不含税暂估：借1405库存商品 贷2202应付账款(明细不含税合计)', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'PUR_INSTOCK');

INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-SAL-OUTSTOCK', '销售出库收入成本凭证', 'SAL_OUTSTOCK',
       '[{"side":"DEBIT","subjectCode":"1122","amountExpr":"order_total","summary":"销售出库 {单号} 确认应收"},{"side":"CREDIT","subjectCode":"6001","amountExpr":"order_total","summary":"销售出库 {单号} 确认收入"},{"side":"DEBIT","subjectCode":"6401","amountExpr":"detail_cost_total","summary":"销售出库 {单号} 结转销售成本"},{"side":"CREDIT","subjectCode":"1405","amountExpr":"detail_cost_total","summary":"销售出库 {单号} 结转库存商品"}]',
       1, 100, '收入(不含税)+成本结转(actual_cost快照,为0跳过成本腿)', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'SAL_OUTSTOCK');

INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-SAL-RET', '销售退货红字凭证', 'SAL_RET',
       '[{"side":"DEBIT","subjectCode":"1122","amountExpr":"order_total","summary":"销售退货 {单号} 冲减应收"},{"side":"CREDIT","subjectCode":"6001","amountExpr":"order_total","summary":"销售退货 {单号} 冲减收入"},{"side":"DEBIT","subjectCode":"6401","amountExpr":"detail_cost_total","summary":"销售退货 {单号} 冲减销售成本"},{"side":"CREDIT","subjectCode":"1405","amountExpr":"detail_cost_total","summary":"销售退货 {单号} 冲减库存商品"}]',
       1, 100, '红字：与销售出库同规则，引擎对金额取负', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'SAL_RET');

INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-PUR-RET', '采购退货红字凭证', 'PUR_RET',
       '[{"side":"DEBIT","subjectCode":"1405","amountExpr":"detail_tax_excl_total","summary":"采购退货 {单号} 冲减库存商品"},{"side":"CREDIT","subjectCode":"2202","amountExpr":"detail_tax_excl_total","summary":"采购退货 {单号} 冲减暂估应付"}]',
       1, 100, '红字：与采购入库暂估同规则，引擎对金额取负', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'PUR_RET');

INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-INVOICE-SALES', '销项发票税额凭证', 'INVOICE_SALES',
       '[{"side":"DEBIT","subjectCode":"1122","amountExpr":"tax_total","summary":"销项发票 {单号} 确认销项税应收"},{"side":"CREDIT","subjectCode":"222101","amountExpr":"tax_total","summary":"销项发票 {单号} 销项税额"}]',
       1, 100, '出库已按不含税确认应收,开票补税差;红字票金额已为负数', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'INVOICE_SALES');

INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-INVOICE-PURCHASE', '进项发票税额凭证', 'INVOICE_PURCHASE',
       '[{"side":"DEBIT","subjectCode":"222101","amountExpr":"tax_total","summary":"进项发票 {单号} 确认进项税额"},{"side":"CREDIT","subjectCode":"2202","amountExpr":"tax_total","summary":"进项发票 {单号} 补记应付税差"}]',
       1, 100, '入库已按不含税暂估,认证补税差;红字票金额已为负数', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'INVOICE_PURCHASE');

INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-RECEIPT', '收款凭证', 'RECEIPT',
       '[{"side":"DEBIT","subjectCode":"@account","amountExpr":"order_total","summary":"收款单 {单号} 收款入账"},{"side":"CREDIT","subjectCode":"1122","amountExpr":"order_total","summary":"收款单 {单号} 核销应收"}]',
       1, 100, '@account按收款账户类型解析:CASH→1001库存现金/BANK→1002银行存款', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'RECEIPT');

INSERT INTO `fin_voucher_template` (`code`, `name`, `biz_type`, `entry_rules`, `enabled`, `priority`, `remark`, `create_by`, `update_by`)
SELECT 'TPL-PAYMENT', '付款凭证', 'PAYMENT',
       '[{"side":"DEBIT","subjectCode":"2202","amountExpr":"order_total","summary":"付款单 {单号} 核销应付"},{"side":"CREDIT","subjectCode":"@account","amountExpr":"order_total","summary":"付款单 {单号} 付款出账"}]',
       1, 100, '@account按付款账户类型解析:CASH→1001库存现金/BANK→1002银行存款', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_voucher_template` WHERE `biz_type` = 'PAYMENT');

-- ---------------------------------------------------------------------
-- 4. 总账会计期间表（与 erp-cost 的 cost_period 成本期间相互独立）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fin_period`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '期间ID',
    `tenant_id`   BIGINT      NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `org_id`      BIGINT      NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `book_id`     BIGINT      NOT NULL DEFAULT 1001 COMMENT '账套ID(fin_book.id,期间按账套隔离)',
    `period`      VARCHAR(6)  NOT NULL COMMENT '会计期间(yyyyMM,凭证日期所在自然月)',
    `status`      VARCHAR(10) NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN开启 CLOSED已结账(禁增删改过账该期凭证)',
    `close_by`    BIGINT      DEFAULT NULL COMMENT '结账人',
    `close_time`  DATETIME    DEFAULT NULL COMMENT '结账时间',
    `reopen_by`   BIGINT      DEFAULT NULL COMMENT '最近反结账人',
    `reopen_time` DATETIME    DEFAULT NULL COMMENT '最近反结账时间',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_period_book` (`book_id`, `period`),
    KEY `idx_period_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '总账会计期间(结账/反结账控制)';

-- ---------------------------------------------------------------------
-- 5. 菜单与按钮权限种子（挂财务管理目录 250 下；凭证模板 274 / 会计期间 275）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 274, 250, '凭证模板', 2, 'finance/voucher-templates', 'finance/voucherTemplates/TemplateList', 'finance:voucherTemplate:list', NULL, 10, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 274);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 2741, 274, '凭证模板新增', 3, NULL, NULL, 'finance:voucherTemplate:add', NULL, 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 2741);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 2742, 274, '凭证模板修改', 3, NULL, NULL, 'finance:voucherTemplate:edit', NULL, 2, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 2742);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 2743, 274, '凭证模板删除', 3, NULL, NULL, 'finance:voucherTemplate:delete', NULL, 3, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 2743);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 275, 250, '会计期间', 2, 'finance/periods', 'finance/periods/PeriodList', 'finance:period:list', NULL, 11, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 275);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 2751, 275, '期间结账/反结账', 3, NULL, NULL, 'finance:period:audit', NULL, 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 2751);

-- super_admin 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`id`
FROM `sys_menu` m
WHERE m.`id` IN (274, 2741, 2742, 2743, 275, 2751)
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`);
