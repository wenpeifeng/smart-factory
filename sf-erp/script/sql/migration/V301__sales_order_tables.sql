-- =====================================================================
-- V301: 销售管理 - 销售订单表（erp-sales）
-- 范围：sal_order（订单头）/ sal_order_detail（订单明细行）
-- 约定：
--   * 统一审计列 id/create_by/create_time/update_by/update_time/deleted；
--   * 金额 DECIMAL(18,2)，数量 DECIMAL(18,4)；
--   * 状态机（单向流转，禁止跳跃）：0草稿 → 10已审核 → 20部分出库 → 30已完成
--     其中 10/20 可 → 90已关闭；0 可逻辑删除；
--   * 审核时做客户信用额度校验（见 SalOrderServiceImpl#audit）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sal_order` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`     VARCHAR(40)   NOT NULL COMMENT '订单号(SO+yyyyMMdd+4位流水)',
    `customer_id`  BIGINT        NOT NULL COMMENT '客户ID(base_customer.id)',
    `warehouse_id` BIGINT        NOT NULL COMMENT '发货仓库ID(base_warehouse.id)',
    `order_date`   DATE          NOT NULL COMMENT '订单日期',
    `total_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '总数量',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '总金额(含税,明细金额合计)',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已审核 20部分出库 30已完成 90已关闭',
    `remark`       VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `audit_by`     BIGINT        DEFAULT NULL COMMENT '审核人',
    `audit_time`   DATETIME      DEFAULT NULL COMMENT '审核时间',
    `close_by`     BIGINT        DEFAULT NULL COMMENT '关闭人',
    `close_time`   DATETIME      DEFAULT NULL COMMENT '关闭时间',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_order_customer` (`customer_id`),
    KEY `idx_order_status` (`status`),
    KEY `idx_order_date` (`order_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售订单表';

CREATE TABLE IF NOT EXISTS `sal_order_detail` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`    BIGINT        NOT NULL COMMENT '订单ID(sal_order.id)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`         DECIMAL(18,4) NOT NULL COMMENT '订购数量',
    `price`       DECIMAL(18,2) NOT NULL COMMENT '含税单价',
    `amount`      DECIMAL(18,2) NOT NULL COMMENT '金额=qty*price 四舍五入2位',
    `out_qty`     DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已出库数量(出库单审核时累加)',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '行备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_od_order` (`order_id`),
    KEY `idx_od_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售订单明细表';
