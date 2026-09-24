-- =====================================================================
-- V141: 供应商准入 + 交易评级 —— basedata 版本段 V14x
-- 模块：erp-biz-basedata（准入/评级代码均落 basedata，评级表前缀 pur_ 与
--       V131 pur_price_record 同理：按约定表直查采购/QC 表，不 import 对方代码）
-- 内容：
--   1. base_supplier_admit  供应商准入申请（登记→申请→审批→通过启用/驳回/
--      黑名单；资质附件字段；复用 workflow bizType=supplier_admit 双通道审批）
--   2. base_supplier.is_blacklist 黑名单标记列（幂等加列；采购下单/审核拦截依据）
--   3. pur_supplier_score   供应商交易评分（月度/季度：交易额、准时交货率、
--      来料合格率、退货率 → 加权总分 + A/B/C/D 等级）
--   4. 菜单/按钮权限种子：208x 挂基础资料目录(200)；218x 挂采购管理目录(210)
--   5. 字典 wf_biz_type 补 supplier_admit（准入审批可挂审批流）
-- 说明：全部幂等（建表 IF NOT EXISTS；加列先查 information_schema）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 供应商准入申请
--    登记即建 base_supplier（status=0 停用，编码 GYS 自动生成）；
--    审批通过 → base_supplier.status=1（启用）；驳回 → 保持停用可改后重提；
--    黑名单 → status=40 且 base_supplier.is_blacklist=1（采购下单/审核拦截）。
--    资质附件字段：license_file 营业执照、cert_files 其他资质（逗号分隔多文件）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_supplier_admit` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=内置租户)',
    `supplier_id`   BIGINT        NULL COMMENT '供应商ID(base_supplier.id,登记时创建)',
    `supplier_name` VARCHAR(100)  NOT NULL COMMENT '供应商名称',
    `credit_code`   VARCHAR(30)   NULL COMMENT '统一社会信用代码',
    `contact`       VARCHAR(50)   NULL COMMENT '联系人',
    `phone`         VARCHAR(20)   NULL COMMENT '手机号',
    `address`       VARCHAR(200)  NULL COMMENT '地址',
    `license_file`  VARCHAR(500)  NULL COMMENT '营业执照附件(文件标识/URL)',
    `cert_files`    VARCHAR(1000) NULL COMMENT '其他资质附件(逗号分隔多文件)',
    `bank_info`     VARCHAR(200)  NULL COMMENT '开户行/账号(可选)',
    `apply_reason`  VARCHAR(200)  NULL COMMENT '申请说明',
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '准入状态:0草稿 10待审 20通过 30驳回 40黑名单',
    `wf_started`    TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已发起workflow审批:0否(管理员审批) 1是(待办审批,lazy同步结果)',
    `audit_by`      BIGINT        NULL COMMENT '审批人(0=工作流自动通过)',
    `audit_time`    DATETIME      NULL COMMENT '审批时间',
    `audit_remark`  VARCHAR(200)  NULL COMMENT '审批意见',
    `blacklist_time` DATETIME     NULL COMMENT '拉黑时间',
    `blacklist_reason` VARCHAR(200) NULL COMMENT '拉黑原因',
    `remark`        VARCHAR(200)  NULL COMMENT '备注',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_admit_supplier` (`supplier_id`),
    KEY `idx_admit_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商准入申请(登记→审批→启用/驳回/黑名单)';

-- ---------------------------------------------------------------------
-- 2. base_supplier 黑名单标记列（幂等；=1 时采购订单创建/审核拦截）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_supplier' AND COLUMN_NAME = 'is_blacklist') = 0,
    'ALTER TABLE base_supplier ADD COLUMN is_blacklist TINYINT NOT NULL DEFAULT 0 COMMENT ''黑名单:0否 1是(准入审批拉黑,采购下单拦截)'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 供应商交易评分（月度/季度汇总，重算=先删后插，uk 兜底防重）
--    准时交货率 = 入库日期 ≤ 订单最早要求交期的入库单数 / 有交期入库单数
--    来料合格率 = IQC 合格数量 / (合格+不合格)
--    退货率     = 期间退货数量 / 期间入库数量（同口径近似，封顶 100%）
--    总分 = 100 × (30%×准时率 + 40%×合格率 + 30%×(1-退货率))，HALF_UP 2 位
--    等级：A≥90 B≥75 C≥60 D<60（详见 docs/modules/supplier-admit-rating.md）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_supplier_score` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`         BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `tenant_id`      BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=内置租户)',
    `supplier_id`    BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `period_type`    VARCHAR(10)   NOT NULL COMMENT '周期类型:MONTH月度 QUARTER季度',
    `period`         VARCHAR(10)   NOT NULL COMMENT '周期标识:yyyy-MM / yyyy-Qn',
    `period_start`   DATE          NOT NULL COMMENT '周期起(含)',
    `period_end`     DATE          NOT NULL COMMENT '周期止(含)',
    `order_count`    INT           NOT NULL DEFAULT 0 COMMENT '期间已审核订单数',
    `order_amount`   DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '期间交易额(订单价税合计)',
    `instock_count`  INT           NOT NULL DEFAULT 0 COMMENT '期间已审核入库单数',
    `instock_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '期间入库数量',
    `ontime_rate`    DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '准时交货率%(入库日≤订单最早交期/有交期入库单)',
    `qualified_rate` DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '来料合格率%(IQC合格数/检验数)',
    `return_rate`    DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '退货率%(期间退货数量/入库数量,封顶100)',
    `total_score`    DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '加权总分(0-100,HALF_UP 2位)',
    `grade`          VARCHAR(2)    NOT NULL DEFAULT 'D' COMMENT '等级:A≥90 B≥75 C≥60 D<60',
    `calc_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sup_score_period` (`supplier_id`, `period_type`, `period`),
    KEY `idx_sup_score_period` (`period_type`, `period`),
    KEY `idx_sup_score_grade` (`grade`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商交易评分(月/季度:交易额+准时率+合格率+退货率加权,扫描器自动+手工重算)';

-- ---------------------------------------------------------------------
-- 4. 菜单/按钮权限种子
--    准入页挂基础资料目录(200，菜单 ID 208 段未占用)；评级报表挂采购管理目录(210，ID 218 段未占用)。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(208, 200, '供应商准入', 2, 'basedata/supplier-admit', 'basedata/suppliers/SupplierAdmit', 'basedata:supplierAdmit:list', NULL, 8, 1, 1),
(2081, 208, '准入登记', 3, NULL, NULL, 'basedata:supplierAdmit:add',      NULL, 1, 1, 1),
(2082, 208, '准入编辑', 3, NULL, NULL, 'basedata:supplierAdmit:edit',    NULL, 2, 1, 1),
(2083, 208, '准入审批操作', 3, NULL, NULL, 'basedata:supplierAdmit:audit', NULL, 3, 1, 1),
(2084, 208, '准入删除', 3, NULL, NULL, 'basedata:supplierAdmit:delete',  NULL, 4, 1, 1),
(2085, 208, '黑名单管理', 3, NULL, NULL, 'basedata:supplierAdmit:blacklist', NULL, 5, 1, 1),
(218, 210, '供应商评级', 2, 'purchase/supplier-score', 'purchase/suppliers/SupplierScoreReport', 'purchase:supplierScore:list', NULL, 9, 1, 1),
(2181, 218, '评分重算', 3, NULL, NULL, 'purchase:supplierScore:recalc', NULL, 1, 1, 1);

-- super_admin(role_id=1) 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (208, 2081, 2082, 2083, 2084, 2085, 218, 2181);

-- ---------------------------------------------------------------------
-- 5. 字典 wf_biz_type 补充 supplier_admit（准入审批可挂审批流；
--    无启用流程定义时 startFlow 返回 SKIPPED，走管理员简单审批）
-- ---------------------------------------------------------------------
INSERT INTO `sys_dict_data` (`dict_type`, `label`, `value`, `sort`, `status`, `remark`)
SELECT 'wf_biz_type', '供应商准入', 'supplier_admit', 9, 1, '供应商准入申请审批(V141准入评级)'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wf_biz_type' AND `value` = 'supplier_admit'
);
