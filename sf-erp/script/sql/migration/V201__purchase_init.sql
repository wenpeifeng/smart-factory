-- =====================================================================
-- V201: 采购管理模块建表（erp-purchase）
-- 内容：
--   1) pur_order / pur_order_detail      采购订单（主/子）
--   2) pur_instock / pur_instock_detail  采购入库单（主/子）
--   3) pur_return / pur_return_detail    采购退货单（主/子）
-- 约定：
--   * 所有表带 id/create_by/create_time/update_by/update_time/deleted 六列；
--   * 金额 DECIMAL(18,2)，数量 DECIMAL(18,4)，单价 DECIMAL(18,4)；
--   * 状态列注释标明取值，只允许正向流转；
--   * 本模块【不创建】他人模块的表：base_*（V101）、inv_stock/inv_movement
--     （V401）、fin_payable（V501）由各自 owner 脚本建表，采购模块只按
--     跨模块约定 INSERT/UPDATE（见 docs/modules/purchase.md）。
--   * 本脚本不向 basedata（base_*）等他人模块的表插入任何种子数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购订单主表
-- 状态机: 0草稿 -> 10已审核 -> 20部分入库 -> 30已完成; 10/20 -> 90已关闭
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_order` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`    VARCHAR(30)   NOT NULL COMMENT '订单号(PO+yyyyMMdd+4位流水)',
    `supplier_id` BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `warehouse_id` BIGINT       DEFAULT NULL COMMENT '默认入库仓库ID(base_warehouse.id)',
    `order_date`  DATE          NOT NULL COMMENT '订单日期',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '价税合计=明细含税金额之和',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已审核 20部分入库 30已完成 90已关闭',
    `audit_by`    BIGINT        DEFAULT NULL COMMENT '审核人',
    `audit_time`  DATETIME      DEFAULT NULL COMMENT '审核时间',
    `remark`      VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_order_date` (`order_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购订单主表';

-- ---------------------------------------------------------------------
-- 2. 采购订单明细表（金额=数量×含税单价，HALF_UP 保留2位，服务端计算）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_order_detail` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`       BIGINT        NOT NULL COMMENT '采购订单ID(pur_order.id)',
    `material_id`    BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`            DECIMAL(18,4) NOT NULL COMMENT '采购数量(>0)',
    `price`          DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '含税单价',
    `tax_rate`       DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '税率(%,如13.00)',
    `amount`         DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '含税金额=qty*price(HALF_UP 2位)',
    `delivery_date`  DATE          DEFAULT NULL COMMENT '要求交货日期',
    `received_qty`   DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '累计已入库数量(入库审核+ / 退货审核-)',
    `remark`         VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_material_id` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购订单明细表';

-- ---------------------------------------------------------------------
-- 3. 采购入库单主表（从采购订单生成，审核时写库存与应付）
-- 状态机: 0草稿 -> 10已审核
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_instock` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `instock_no`   VARCHAR(30)   NOT NULL COMMENT '入库单号(RK+yyyyMMdd+4位流水)',
    `order_id`     BIGINT        NOT NULL COMMENT '来源采购订单ID(pur_order.id)',
    `supplier_id`  BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `warehouse_id` BIGINT        NOT NULL COMMENT '入库仓库ID(base_warehouse.id)',
    `instock_date` DATE          NOT NULL COMMENT '入库日期',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '价税合计=明细含税金额之和',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已审核',
    `audit_by`     BIGINT        DEFAULT NULL COMMENT '审核人',
    `audit_time`   DATETIME      DEFAULT NULL COMMENT '审核时间',
    `remark`       VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_instock_no` (`instock_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_instock_date` (`instock_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购入库单主表';

-- ---------------------------------------------------------------------
-- 4. 采购入库单明细表（实收数量可小于订单数量，审核时写库存流水）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_instock_detail` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `instock_id`      BIGINT        NOT NULL COMMENT '入库单ID(pur_instock.id)',
    `order_detail_id` BIGINT        NOT NULL COMMENT '来源订单明细ID(pur_order_detail.id,用于回写订单累计入库数)',
    `material_id`     BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`             DECIMAL(18,4) NOT NULL COMMENT '实收数量(>0)',
    `price`           DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '含税单价',
    `tax_rate`        DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '税率(%)',
    `amount`          DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '含税金额=qty*price(HALF_UP 2位)',
    `returned_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '本明细累计被退货数量',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_instock_id` (`instock_id`),
    KEY `idx_order_detail_id` (`order_detail_id`),
    KEY `idx_material_id` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购入库单明细表';

-- ---------------------------------------------------------------------
-- 5. 采购退货单主表（引用采购入库单，审核时反向库存事务）
-- 状态机: 0草稿 -> 10已审核
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_return` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `return_no`    VARCHAR(30)   NOT NULL COMMENT '退货单号(RT+yyyyMMdd+4位流水)',
    `instock_id`   BIGINT        NOT NULL COMMENT '来源入库单ID(pur_instock.id)',
    `supplier_id`  BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `warehouse_id` BIGINT        NOT NULL COMMENT '退货出库仓库ID(base_warehouse.id)',
    `return_date`  DATE          NOT NULL COMMENT '退货日期',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '价税合计=明细含税金额之和(退货冲减)',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已审核',
    `audit_by`     BIGINT        DEFAULT NULL COMMENT '审核人',
    `audit_time`   DATETIME      DEFAULT NULL COMMENT '审核时间',
    `remark`       VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_return_no` (`return_no`),
    KEY `idx_instock_id` (`instock_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_return_date` (`return_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购退货单主表';

-- ---------------------------------------------------------------------
-- 6. 采购退货单明细表（退货数量<=入库明细数量-已退数量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_return_detail` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `return_id`         BIGINT        NOT NULL COMMENT '退货单ID(pur_return.id)',
    `instock_detail_id` BIGINT        NOT NULL COMMENT '来源入库明细ID(pur_instock_detail.id)',
    `material_id`       BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`               DECIMAL(18,4) NOT NULL COMMENT '退货数量(>0)',
    `price`             DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '含税单价',
    `tax_rate`          DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '税率(%)',
    `amount`            DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '含税金额=qty*price(HALF_UP 2位)',
    `remark`            VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`         BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_return_id` (`return_id`),
    KEY `idx_instock_detail_id` (`instock_detail_id`),
    KEY `idx_material_id` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购退货单明细表';
