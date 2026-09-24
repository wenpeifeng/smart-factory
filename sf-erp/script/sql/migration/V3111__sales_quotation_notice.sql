-- =====================================================================
-- V3111: 销售前端链路深化 - 报价单 + 发货通知单（erp-biz-sales）
-- 内容：
--   1. sal_quotation / sal_quotation_item：销售报价单（BJ 前缀）；
--      状态机：0草稿 →(提交报价)→ 10已报价 →(下推)→ 20已转订单（终态）；
--      10 且 valid_until < 当天 = 30已过期（查询时动态判定，不落库）；
--      0/10 →(作废)→ 40已作废（终态）；
--   2. sal_delivery_notice / sal_delivery_notice_item：发货通知单（FH 前缀，
--      来源订单可空=支持无来源直发）；状态机：0草稿 →(通知)→ 10已通知
--      →(全部核销)→ 20已出库（终态）；0/10（且未发生出库）→(取消)→ 30已取消；
--   3. 菜单/按钮权限种子：sales:quotation:{list,add,edit,delete,audit,toOrder}、
--      sales:deliveryNotice:{list,add,edit,delete,audit,cancel,settle}，
--      挂"销售管理"目录（220，参照 V1101 树）+ super_admin 角色关联；
-- 约定：
--   * 建表含 org_id/tenant_id（V1012 全表约定，MP 自动填充：业务表 org 默认 1）；
--   * 金额 DECIMAL(18,2)、数量 DECIMAL(18,4)，逻辑删除 deleted；
--   * 报价行 amount = qty × price × (1 - 整单折扣率/100) 四舍五入 2 位；
--   * 通知行 out_qty 由"按出库单号核销"接口累加（守卫不超通知量）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sal_quotation` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=本部)',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=演示租户)',
    `quotation_no`  VARCHAR(40)   NOT NULL COMMENT '报价单号(BJ+yyyyMMdd+流水)',
    `customer_id`   BIGINT        NOT NULL COMMENT '客户ID(base_customer.id)',
    `quote_date`    DATE          NOT NULL COMMENT '报价日期',
    `valid_until`   DATE          NOT NULL COMMENT '有效期至(当天含在内,过期=valid_until<当天)',
    `discount_rate` DECIMAL(5,2)  DEFAULT NULL COMMENT '整单折扣率%(0-100,如10表示九折,空=不打折)',
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已报价 20已转订单 40已作废(30已过期为查询时动态判定,不落库)',
    `order_id`      BIGINT        DEFAULT NULL COMMENT '下推生成的销售订单ID(sal_order.id)',
    `order_no`      VARCHAR(40)   DEFAULT NULL COMMENT '下推生成的销售订单号',
    `submit_by`     BIGINT        DEFAULT NULL COMMENT '提交报价人',
    `submit_time`   DATETIME      DEFAULT NULL COMMENT '提交报价时间',
    `void_by`       BIGINT        DEFAULT NULL COMMENT '作废人',
    `void_time`     DATETIME      DEFAULT NULL COMMENT '作废时间',
    `remark`        VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quotation_no` (`quotation_no`),
    KEY `idx_quotation_customer` (`customer_id`),
    KEY `idx_quotation_status` (`status`),
    KEY `idx_quotation_valid_until` (`valid_until`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售报价单表';

CREATE TABLE IF NOT EXISTS `sal_quotation_item` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`       BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=本部)',
    `tenant_id`    BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=演示租户)',
    `quotation_id` BIGINT        NOT NULL COMMENT '报价单ID(sal_quotation.id)',
    `material_id`  BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`          DECIMAL(18,4) NOT NULL COMMENT '报价数量',
    `price`        DECIMAL(18,2) NOT NULL COMMENT '报价单价',
    `cost_price`   DECIMAL(18,2) DEFAULT NULL COMMENT '成本参考(base_material.ref_cost录入时快照,只读带回)',
    `amount`       DECIMAL(18,2) NOT NULL COMMENT '行金额=qty*price*(1-整单折扣率/100) 四舍五入2位',
    `remark`       VARCHAR(200)  DEFAULT NULL COMMENT '行备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_qi_quotation` (`quotation_id`),
    KEY `idx_qi_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售报价单明细表';

CREATE TABLE IF NOT EXISTS `sal_delivery_notice` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`       BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=本部)',
    `tenant_id`    BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=演示租户)',
    `notice_no`    VARCHAR(40)   NOT NULL COMMENT '通知单号(FH+yyyyMMdd+流水)',
    `order_id`     BIGINT        DEFAULT NULL COMMENT '来源销售订单ID(sal_order.id,空=无来源直发)',
    `order_no`     VARCHAR(40)   DEFAULT NULL COMMENT '来源销售订单号(冗余展示)',
    `customer_id`  BIGINT        NOT NULL COMMENT '客户ID(base_customer.id)',
    `warehouse_id` BIGINT        NOT NULL COMMENT '发货仓库ID(base_warehouse.id)',
    `plan_date`    DATE          NOT NULL COMMENT '计划发货日期',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已通知 20已出库 30已取消',
    `notify_by`    BIGINT        DEFAULT NULL COMMENT '通知人',
    `notify_time`  DATETIME      DEFAULT NULL COMMENT '通知时间',
    `cancel_by`    BIGINT        DEFAULT NULL COMMENT '取消人',
    `cancel_time`  DATETIME      DEFAULT NULL COMMENT '取消时间',
    `settle_by`    BIGINT        DEFAULT NULL COMMENT '最近核销人',
    `settle_time`  DATETIME      DEFAULT NULL COMMENT '最近核销时间',
    `remark`       VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_no` (`notice_no`),
    KEY `idx_dn_order` (`order_id`),
    KEY `idx_dn_customer` (`customer_id`),
    KEY `idx_dn_status` (`status`),
    KEY `idx_dn_plan_date` (`plan_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售发货通知单表';

CREATE TABLE IF NOT EXISTS `sal_delivery_notice_item` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`          BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=本部)',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=演示租户)',
    `notice_id`       BIGINT        NOT NULL COMMENT '通知单ID(sal_delivery_notice.id)',
    `material_id`     BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`             DECIMAL(18,4) NOT NULL COMMENT '通知数量',
    `out_qty`         DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已出库数量(按出库单号核销累加,恒≤qty)',
    `order_detail_id` BIGINT        DEFAULT NULL COMMENT '来源订单明细ID(sal_order_detail.id,空=直发行)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '行备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_ni_notice` (`notice_id`),
    KEY `idx_ni_order_detail` (`order_detail_id`),
    KEY `idx_ni_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售发货通知单明细表';

-- ============ 菜单与按钮权限种子（挂销售管理目录 220，参照 V1101 树） ============
INSERT INTO `sys_menu` (`id`, `org_id`, `tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(225,  0, 1000, 220, '报价单',     2, 'sales/quotations', 'sales/quotations/QuotationList', 'sales:quotation:list',     NULL, 5, 1, 1),
(2251, 0, 1000, 225, '报价单新增', 3, NULL, NULL, 'sales:quotation:add',     NULL, 1, 1, 1),
(2252, 0, 1000, 225, '报价单修改', 3, NULL, NULL, 'sales:quotation:edit',    NULL, 2, 1, 1),
(2253, 0, 1000, 225, '报价单删除', 3, NULL, NULL, 'sales:quotation:delete',  NULL, 3, 1, 1),
(2254, 0, 1000, 225, '提交报价',   3, NULL, NULL, 'sales:quotation:audit',   NULL, 4, 1, 1),
(2255, 0, 1000, 225, '转销售订单', 3, NULL, NULL, 'sales:quotation:toOrder', NULL, 5, 1, 1),
(226,  0, 1000, 220, '发货通知单', 2, 'sales/notices', 'sales/notices/NoticeList', 'sales:deliveryNotice:list', NULL, 6, 1, 1),
(2261, 0, 1000, 226, '通知单新增', 3, NULL, NULL, 'sales:deliveryNotice:add',    NULL, 1, 1, 1),
(2262, 0, 1000, 226, '通知单修改', 3, NULL, NULL, 'sales:deliveryNotice:edit',   NULL, 2, 1, 1),
(2263, 0, 1000, 226, '通知单删除', 3, NULL, NULL, 'sales:deliveryNotice:delete', NULL, 3, 1, 1),
(2264, 0, 1000, 226, '通知发货',   3, NULL, NULL, 'sales:deliveryNotice:audit',  NULL, 4, 1, 1),
(2265, 0, 1000, 226, '取消通知',   3, NULL, NULL, 'sales:deliveryNotice:cancel', NULL, 5, 1, 1),
(2266, 0, 1000, 226, '核销出库',   3, NULL, NULL, 'sales:deliveryNotice:settle', NULL, 6, 1, 1);

-- super_admin 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (225, 2251, 2252, 2253, 2254, 2255, 226, 2261, 2262, 2263, 2264, 2265, 2266);
