-- =====================================================================
-- V962 组织管理(erp-org)初始化之二：组织间调拨单（头 + 明细）
--   1) org_transfer         调拨单头：单号 IT+yyyyMMdd+3位流水（Redis INCR），
--                           状态机 0草稿 → 1已审核（过账）/ 2已取消（仅草稿可取消）
--   2) org_transfer_detail  明细行：数量 + 成本单价（调出方最近加权价，取自
--                           cost_flow 约定表最新运行余额 balance_cost/balance_qty）
--                           + 内部结算单价/金额（按价目 加价率或固定加价额）
-- 跨模块约定：审核过账时按约定写 inv_stock / inv_movement（ORG_OUT/ORG_IN），
-- 不依赖 inv_stock 唯一键结构（UPSERT 兜底），不 import inventory 模块代码。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `org_transfer`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `transfer_no`       VARCHAR(40)  NOT NULL COMMENT '调拨单号(IT+yyyyMMdd+3位流水,Redis自增)',
    `from_factory_id`   BIGINT       NOT NULL COMMENT '调出工厂ID(org_factory.id)',
    `from_warehouse_id` BIGINT       NOT NULL COMMENT '调出仓库ID(base_warehouse.id,须归属调出工厂)',
    `to_factory_id`     BIGINT       NOT NULL COMMENT '调入工厂ID(org_factory.id)',
    `to_warehouse_id`   BIGINT       NOT NULL COMMENT '调入仓库ID(base_warehouse.id,须归属调入工厂)',
    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0草稿 1已审核 2已取消',
    `audit_by`          BIGINT       DEFAULT NULL COMMENT '审核人(用户ID)',
    `audit_time`        DATETIME     DEFAULT NULL COMMENT '审核时间',
    `remark`            VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`         BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_org_transfer_no` (`transfer_no`),
    KEY `idx_org_transfer_status` (`status`, `create_time`),
    KEY `idx_org_transfer_line` (`from_factory_id`, `to_factory_id`),
    KEY `idx_org_transfer_wh` (`from_warehouse_id`, `to_warehouse_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '组织间调拨单(工厂间实物调拨,审核过账)';

CREATE TABLE IF NOT EXISTS `org_transfer_detail`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `transfer_id`    BIGINT        NOT NULL COMMENT '调拨单ID(org_transfer.id)',
    `material_id`    BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`            DECIMAL(18,4) NOT NULL COMMENT '调拨数量(>0)',
    `cost_price`     DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '成本单价(调出方最近加权价=cost_flow.balance_cost/balance_qty,无数据为0)',
    `settle_price`   DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '内部结算单价:RATE=成本×(1+加价率%) FIXED=成本+固定加价额(无价目=成本)',
    `settle_amount`  DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '内部结算金额=结算单价×数量',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_otd_transfer` (`transfer_id`),
    KEY `idx_otd_material` (`material_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '组织间调拨单明细(成本/内部结算价快照)';
