-- =====================================================================
-- V2111: 采购前端链路深化 —— 请购单 + 询比价单（V211x 段）
-- 内容：
--   1) pur_requisition / pur_requisition_item   请购单（主/子）
--   2) pur_rfq / pur_rfq_supplier / pur_rfq_item / pur_rfq_quote
--      询比价单（主表 / 供应商 / 询价明细 / 报价行）
--   3) 采购管理目录下新增「请购管理」「询比价」菜单与按钮权限码
--      （purchase:requisition:{list,add,edit,delete,audit,close}、
--        purchase:rfq:{list,add,edit,delete,award,toOrder}）
-- 链路：请购单(提交) -> 询比价(邀请供应商/录入报价/比价/分项定标)
--       -> 下推采购订单草稿(调既有 PurOrderService，一供应商一单)。
-- 约定：
--   * 单号统一走 OrderNoGenerator（Redis INCR，4 位流水，与 PO/RK/RT 一致）：
--     请购单 PR+yyyyMMdd+4 位流水，询比价单 IN+yyyyMMdd+4 位流水；
--   * tenant_id/org_id 两列必带（V1012 全表规范，业务表 org 默认 1）；
--   * 已转采购数量 ordered_qty 只在【定标】事务内条件 UPDATE 回写，
--     数据库层兜底 ordered_qty + delta <= qty，防并发超转；
--   * RFQ 明细回写 pur_order_id 在【下推】事务内完成，一明细只允许下推一次。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 请购单主表
-- 状态机: 0草稿 -> 10已提交 -> 20已转采购; 10/20 可人工关闭为 30已关闭(终态)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_requisition` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`       BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=本部)',
    `tenant_id`    BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=演示租户)',
    `req_no`       VARCHAR(30)   NOT NULL COMMENT '请购单号(PR+yyyyMMdd+4位流水,Redis生成)',
    `dept_id`      BIGINT        NOT NULL COMMENT '请购部门ID(sys_dept.id)',
    `applicant_id` BIGINT        DEFAULT NULL COMMENT '请购人ID(sys_user.id,缺省当前登录用户)',
    `expect_date`  DATE          DEFAULT NULL COMMENT '期望到货日期',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已提交 20已转采购 30已关闭',
    `remark`       VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_req_no` (`req_no`),
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_status` (`status`),
    KEY `idx_applicant_id` (`applicant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '请购单主表';

-- ---------------------------------------------------------------------
-- 2. 请购单明细表（已转采购数量由询比价定标回写，累计不得超过请购数量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_requisition_item` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`         BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID',
    `tenant_id`      BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `requisition_id` BIGINT        NOT NULL COMMENT '请购单ID(pur_requisition.id)',
    `material_id`    BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`            DECIMAL(18,4) NOT NULL COMMENT '请购数量(>0)',
    `ordered_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已转采购数量(询比价定标回写,累计<=qty)',
    `require_date`   DATE          DEFAULT NULL COMMENT '需求日期',
    `remark`         VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_requisition_id` (`requisition_id`),
    KEY `idx_material_id` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '请购单明细表';

-- ---------------------------------------------------------------------
-- 3. 询比价单主表（可关联请购单，也可独立发起）
-- 状态机: 0询价中 -> 10已定标; 0可取消为 30已取消(终态); 已定标禁止修改/删除/再报价
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_rfq` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`         BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID',
    `tenant_id`      BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `rfq_no`         VARCHAR(30)   NOT NULL COMMENT '询比价单号(IN+yyyyMMdd+4位流水,Redis生成)',
    `requisition_id` BIGINT        DEFAULT NULL COMMENT '来源请购单ID(pur_requisition.id,可空=独立询价)',
    `deadline`       DATE          DEFAULT NULL COMMENT '报价截止日期',
    `status`         TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0询价中 10已定标 30已取消',
    `award_by`       BIGINT        DEFAULT NULL COMMENT '定标人(sys_user.id)',
    `award_time`     DATETIME      DEFAULT NULL COMMENT '定标时间',
    `remark`         VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rfq_no` (`rfq_no`),
    KEY `idx_requisition_id` (`requisition_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deadline` (`deadline`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '询比价单主表';

-- ---------------------------------------------------------------------
-- 4. 询比价供应商表（一次询价邀请多家，报价金额合计在录报价时回写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_rfq_supplier` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`       BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID',
    `tenant_id`    BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `rfq_id`       BIGINT        NOT NULL COMMENT '询比价单ID(pur_rfq.id)',
    `supplier_id`  BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `quote_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '报价金额合计(按物料数量x报价单价汇总)',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0已邀请 10已报价 20中标',
    `remark`       VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rfq_supplier` (`rfq_id`, `supplier_id`),
    KEY `idx_supplier_id` (`supplier_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '询比价供应商表';

-- ---------------------------------------------------------------------
-- 5. 询比价明细表（定标信息与下推采购订单回写都在行上）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_rfq_item` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`              BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID',
    `tenant_id`           BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `rfq_id`              BIGINT        NOT NULL COMMENT '询比价单ID(pur_rfq.id)',
    `requisition_item_id` BIGINT        DEFAULT NULL COMMENT '来源请购明细ID(pur_requisition_item.id,可空)',
    `material_id`         BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`                 DECIMAL(18,4) NOT NULL COMMENT '询价数量(>0,定标即采购量)',
    `award_supplier_id`   BIGINT        DEFAULT NULL COMMENT '定标供应商ID(定标时写入)',
    `award_price`         DECIMAL(18,4) DEFAULT NULL COMMENT '中标单价(定标时取该供应商报价)',
    `award_lead_days`     INT           DEFAULT NULL COMMENT '中标交期(天,定标时取该供应商报价交期)',
    `pur_order_id`        BIGINT        DEFAULT NULL COMMENT '下推生成的采购订单ID(pur_order.id,一明细仅下推一次)',
    `remark`              VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`           BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_rfq_id` (`rfq_id`),
    KEY `idx_requisition_item_id` (`requisition_item_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_pur_order_id` (`pur_order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '询比价明细表';

-- ---------------------------------------------------------------------
-- 6. 询比价报价行（rfq+供应商+明细 唯一；同供应商重录=整体覆盖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_rfq_quote` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`      BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `rfq_id`      BIGINT        NOT NULL COMMENT '询比价单ID(pur_rfq.id)',
    `supplier_id` BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `rfq_item_id` BIGINT        NOT NULL COMMENT '询比价明细ID(pur_rfq_item.id)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID(base_material.id,冗余便于比价矩阵聚合)',
    `price`       DECIMAL(18,4) NOT NULL COMMENT '报价单价(含税)',
    `lead_days`   INT           NOT NULL DEFAULT 0 COMMENT '交期(天)',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quote` (`rfq_item_id`, `supplier_id`),
    KEY `idx_rfq_id` (`rfq_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_material_id` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '询比价报价行表';

-- ---------------------------------------------------------------------
-- 7. 菜单与按钮权限（挂采购管理目录 210 下，编号沿用 V1101 段位规则）
--    前端路由/菜单文案已由主线程预置（MainLayout + locales）。
-- ---------------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `org_id`, `tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(215, 0, 1000, 210, '请购管理', 2, 'purchase/requisitions', 'purchase/requisitions/RequisitionList', 'purchase:requisition:list', NULL, 0, 1, 1),
(2151, 0, 1000, 215, '请购单新增', 3, NULL, NULL, 'purchase:requisition:add',    NULL, 1, 1, 1),
(2152, 0, 1000, 215, '请购单修改', 3, NULL, NULL, 'purchase:requisition:edit',   NULL, 2, 1, 1),
(2153, 0, 1000, 215, '请购单删除', 3, NULL, NULL, 'purchase:requisition:delete', NULL, 3, 1, 1),
(2154, 0, 1000, 215, '请购单提交', 3, NULL, NULL, 'purchase:requisition:audit',  NULL, 4, 1, 1),
(2155, 0, 1000, 215, '请购单关闭', 3, NULL, NULL, 'purchase:requisition:close',  NULL, 5, 1, 1),
(216, 0, 1000, 210, '询比价', 2, 'purchase/rfqs', 'purchase/rfqs/RfqList', 'purchase:rfq:list', NULL, 5, 1, 1),
(2161, 0, 1000, 216, '询比价新增', 3, NULL, NULL, 'purchase:rfq:add',     NULL, 1, 1, 1),
(2162, 0, 1000, 216, '询比价修改', 3, NULL, NULL, 'purchase:rfq:edit',    NULL, 2, 1, 1),
(2163, 0, 1000, 216, '询比价删除', 3, NULL, NULL, 'purchase:rfq:delete',  NULL, 3, 1, 1),
(2164, 0, 1000, 216, '询比价定标', 3, NULL, NULL, 'purchase:rfq:award',   NULL, 4, 1, 1),
(2165, 0, 1000, 216, '询比价下推订单', 3, NULL, NULL, 'purchase:rfq:toOrder', NULL, 5, 1, 1);

-- super_admin(role_id=1) 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (215, 2151, 2152, 2153, 2154, 2155, 216, 2161, 2162, 2163, 2164, 2165);
