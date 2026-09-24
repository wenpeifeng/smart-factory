-- =====================================================================
-- V302: 销售管理 - 销售出库单表（erp-sales）
-- 范围：sal_outstock（出库头）/ sal_outstock_detail（出库明细行）
-- 关键约束：
--   * 出库单从"已审核/部分出库"的销售订单选单生成，行数量不得超过订单行剩余数量
--     （qty - out_qty），审核时用条件 UPDATE 防并发超发；
--   * 审核成功在一个事务内：扣减 inv_stock（不足则整体回滚）→ 写 inv_movement
--     负数流水 → 生成 fin_receivable 应收 → 回写订单行 out_qty 并刷新订单状态。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sal_outstock` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `out_no`       VARCHAR(40)   NOT NULL COMMENT '出库单号(XS+yyyyMMdd+4位流水)',
    `order_id`     BIGINT        NOT NULL COMMENT '来源销售订单ID(sal_order.id)',
    `order_no`     VARCHAR(40)   NOT NULL COMMENT '来源销售订单号(冗余,便于追溯)',
    `customer_id`  BIGINT        NOT NULL COMMENT '客户ID(base_customer.id)',
    `warehouse_id` BIGINT        NOT NULL COMMENT '出库仓库ID(base_warehouse.id)',
    `out_date`     DATE          NOT NULL COMMENT '出库日期',
    `total_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '总数量',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '总金额(明细金额合计)',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已审核',
    `remark`       VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `audit_by`     BIGINT        DEFAULT NULL COMMENT '审核人',
    `audit_time`   DATETIME      DEFAULT NULL COMMENT '审核时间',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_out_no` (`out_no`),
    KEY `idx_out_order` (`order_id`),
    KEY `idx_out_customer` (`customer_id`),
    KEY `idx_out_status` (`status`),
    KEY `idx_out_date` (`out_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售出库单表';

CREATE TABLE IF NOT EXISTS `sal_outstock_detail` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `out_id`          BIGINT        NOT NULL COMMENT '出库单ID(sal_outstock.id)',
    `order_detail_id` BIGINT        NOT NULL COMMENT '来源订单行ID(sal_order_detail.id)',
    `material_id`     BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`             DECIMAL(18,4) NOT NULL COMMENT '出库数量',
    `price`           DECIMAL(18,2) NOT NULL COMMENT '含税单价(取订单行单价)',
    `amount`          DECIMAL(18,2) NOT NULL COMMENT '金额=qty*price 四舍五入2位',
    `returned_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已被退货数量(销售退货单审核时累加)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '行备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_outd_out` (`out_id`),
    KEY `idx_outd_order_detail` (`order_detail_id`),
    KEY `idx_outd_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售出库单明细表';
