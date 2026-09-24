-- =====================================================================
-- V131: 价格管理（采购价格库 + 销售价格策略）—— basedata 版本段 V13x
-- 模块：erp-biz-basedata 价格中心（新表新文件，不侵入采购/销售既有代码）
-- 内容：
--   1. pur_price_record    采购价格流水（成交价库）：扫描器从已审核采购订单/
--      采购入库单/定标 RFQ 自动沉淀（零侵入，开关 price.auto-collect.enabled），
--      亦支持手工补录（source_type=CONTRACT/历史导入）；
--   2. base_price_contract 采购合同价（CRUD）：录单参照时合同价优先展示（标注"合同"）；
--   3. sal_price_level     销售等级价（物料×客户等级 A/B/C，uk 物料+等级）；
--   4. sal_price_qty       销售批量价（物料×起订量阶梯，uk 物料+起订量）；
--   5. sal_price_special   特价审批（申请→workflow/管理员审批→生效期）；
--   6. base_customer + level 客户等级列（A/B/C，幂等加列，存量默认 B）；
--   7. 菜单/按钮权限种子：basedata:price:*（挂基础资料目录 200）；
--      sales:priceSpecial:*（挂销售管理目录 220）；
--   8. 字典 wf_biz_type 补 sal_price_special（特价审批可选挂审批流）。
-- 说明：全部幂等（information_schema 判断列存在后再 ALTER；建表 IF NOT EXISTS）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购价格流水（成交价库）
--    价格口径：不含税单价 DECIMAL(18,4)。订单/入库明细价是含税价，
--    沉淀时按 price / (1 + tax_rate/100) 换算（HALF_UP 4 位）；
--    RFQ 定标价 / 合同价视为不含税裸价直接入库。
--    uk(source_type, source_detail_id) 保证同一来源行只沉淀一次（幂等）；
--    手工补录行 source_detail_id 为 NULL（MySQL 唯一索引允许多个 NULL）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_price_record` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`           BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `tenant_id`        BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=内置租户)',
    `supplier_id`      BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `material_id`      BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `price`            DECIMAL(18,4) NOT NULL COMMENT '成交单价(不含税;订单/入库按含税价÷(1+税率%)换算)',
    `source_type`      VARCHAR(20)   NOT NULL COMMENT '来源类型:ORDER采购订单/INSTOCK采购入库/RFQ询比价定标/CONTRACT合同或手工',
    `source_no`        VARCHAR(30)   NOT NULL COMMENT '来源单号(PO/RK/IN前缀或合同号,可追溯)',
    `source_detail_id` BIGINT        NULL COMMENT '来源明细行ID(订单行/入库行/询价行;手工补录为NULL)',
    `price_date`       DATE          NOT NULL COMMENT '价格日期(单据业务日期)',
    `remark`           VARCHAR(200)  NULL COMMENT '备注',
    `create_by`        BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_price_source` (`source_type`, `source_detail_id`),
    KEY `idx_price_supplier_mat` (`supplier_id`, `material_id`, `price_date`),
    KEY `idx_price_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购价格流水(成交价库:扫描器自动沉淀+手工补录)';

-- ---------------------------------------------------------------------
-- 2. 采购合同价（参照时优先展示，标注"合同"）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_price_contract` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`         BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `tenant_id`      BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=内置租户)',
    `supplier_id`    BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `material_id`    BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `contract_price` DECIMAL(18,4) NOT NULL COMMENT '合同单价(不含税)',
    `effective_from` DATE          NOT NULL COMMENT '生效日期起(含)',
    `effective_to`   DATE          NOT NULL COMMENT '生效日期止(含)',
    `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`         VARCHAR(200)  NULL COMMENT '备注(合同号等)',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_contract_supplier_mat` (`supplier_id`, `material_id`),
    KEY `idx_contract_effect` (`effective_from`, `effective_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购合同价(录单参照优先展示)';

-- ---------------------------------------------------------------------
-- 3. 销售等级价（物料 × 客户等级 A/B/C）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sal_price_level` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`      BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=内置租户)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `level`       VARCHAR(2)    NOT NULL COMMENT '客户等级:A/B/C(对应 base_customer.level)',
    `price`       DECIMAL(18,4) NOT NULL COMMENT '等级销售单价(不含税)',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200)  NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_price_level` (`material_id`, `level`),
    KEY `idx_price_level_mat` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售等级价(物料×客户等级)';

-- ---------------------------------------------------------------------
-- 4. 销售批量价（物料 × 起订量阶梯：min_qty 越大价越低）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sal_price_qty` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`      BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=内置租户)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `min_qty`     DECIMAL(18,4) NOT NULL COMMENT '起订量(含,满足 qty≥min_qty 的最低价格阶梯)',
    `price`       DECIMAL(18,4) NOT NULL COMMENT '批量销售单价(不含税)',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200)  NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_price_qty` (`material_id`, `min_qty`),
    KEY `idx_price_qty_mat` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售批量价(物料×起订量阶梯)';

-- ---------------------------------------------------------------------
-- 5. 特价审批（申请 → workflow(sal_price_special) 或管理员审批 → 生效）
--    状态:0待审 10通过 20驳回；通过且在生效期内的记录进入定价引擎最高优先级。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sal_price_special` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=默认)',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=内置租户)',
    `customer_id`   BIGINT        NOT NULL COMMENT '客户ID(base_customer.id)',
    `material_id`   BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `special_price` DECIMAL(18,4) NOT NULL COMMENT '特价(不含税)',
    `apply_reason`  VARCHAR(200)  NULL COMMENT '申请理由',
    `valid_from`    DATE          NULL COMMENT '生效起(含,空=不限)',
    `valid_to`      DATE          NULL COMMENT '生效止(含,空=不限)',
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0待审 10通过 20驳回',
    `wf_started`    TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已发起workflow审批:0否(管理员审批) 1是(待办审批,lazy同步结果)',
    `audit_by`      BIGINT        NULL COMMENT '审批人(0=工作流自动通过)',
    `audit_time`    DATETIME      NULL COMMENT '审批时间',
    `audit_remark`  VARCHAR(200)  NULL COMMENT '审批意见',
    `remark`        VARCHAR(200)  NULL COMMENT '备注',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0正常 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_special_customer_mat` (`customer_id`, `material_id`, `status`),
    KEY `idx_special_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '特价审批(客户×物料,通过后定价引擎最高优先级)';

-- ---------------------------------------------------------------------
-- 6. base_customer 客户等级列（幂等，存量默认 B）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_customer' AND COLUMN_NAME = 'level') = 0,
    'ALTER TABLE base_customer ADD COLUMN level VARCHAR(2) NOT NULL DEFAULT ''B'' COMMENT ''客户等级:A/B/C(销售等级价策略匹配,默认B)'' AFTER init_receivable',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 7. 菜单/按钮权限种子
--    价格管理页挂基础资料目录(200)；特价审批权限挂销售管理目录(220)
--    （页面在 basedata/prices/PriceCenter 的"特价审批"Tab，权限归销售口径）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(207, 200, '价格管理', 2, 'basedata/prices', 'basedata/prices/PriceCenter', 'basedata:price:list', NULL, 7, 1, 1),
(2071, 207, '价格库新增', 3, NULL, NULL, 'basedata:price:add',    NULL, 1, 1, 1),
(2072, 207, '价格库修改', 3, NULL, NULL, 'basedata:price:edit',   NULL, 2, 1, 1),
(2073, 207, '价格库删除', 3, NULL, NULL, 'basedata:price:delete', NULL, 3, 1, 1),
(227, 220, '特价审批', 2, 'basedata/prices', 'basedata/prices/PriceCenter', 'sales:priceSpecial:list', NULL, 7, 1, 1),
(2271, 227, '特价申请', 3, NULL, NULL, 'sales:priceSpecial:add',    NULL, 1, 1, 1),
(2272, 227, '特价审批操作', 3, NULL, NULL, 'sales:priceSpecial:audit', NULL, 2, 1, 1),
(2273, 227, '特价删除', 3, NULL, NULL, 'sales:priceSpecial:delete', NULL, 3, 1, 1);

-- super_admin(role_id=1) 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (207, 2071, 2072, 2073, 227, 2271, 2272, 2273);

-- ---------------------------------------------------------------------
-- 8. 字典 wf_biz_type 补充 sal_price_special（特价审批可挂审批流；
--    无启用流程定义时 startFlow 返回 SKIPPED，走管理员简单审批）
-- ---------------------------------------------------------------------
INSERT INTO `sys_dict_data` (`dict_type`, `label`, `value`, `sort`, `status`, `remark`)
SELECT 'wf_biz_type', '特价审批', 'sal_price_special', 8, 1, '销售特价申请审批(V131价格中心)'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wf_biz_type' AND `value` = 'sal_price_special'
);
