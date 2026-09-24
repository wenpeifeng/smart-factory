-- =====================================================================
-- V303: 销售管理 - 销售退货单表（erp-sales）
-- 范围：sal_return（退货头）/ sal_return_detail（退货明细行）
-- 关键约束：
--   * 退货引用"已审核"的销售出库单，行数量不得超过出库行可退数量
--     （qty - returned_qty），审核时用条件 UPDATE 防并发超退；
--   * 审核成功在一个事务内：回补 inv_stock → 写 inv_movement 正数流水
--     → 生成 fin_receivable 红字（负金额）应收 → 回写出库行 returned_qty。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sal_return` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `return_no`    VARCHAR(40)   NOT NULL COMMENT '退货单号(RT+yyyyMMdd+4位流水)',
    `out_id`       BIGINT        NOT NULL COMMENT '来源出库单ID(sal_outstock.id)',
    `out_no`       VARCHAR(40)   NOT NULL COMMENT '来源出库单号(冗余,便于追溯)',
    `customer_id`  BIGINT        NOT NULL COMMENT '客户ID(base_customer.id)',
    `warehouse_id` BIGINT        NOT NULL COMMENT '退回仓库ID(base_warehouse.id)',
    `return_date`  DATE          NOT NULL COMMENT '退货日期',
    `total_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '总数量',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '总金额(正数表述,应收红字取其负值)',
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
    UNIQUE KEY `uk_return_no` (`return_no`),
    KEY `idx_ret_out` (`out_id`),
    KEY `idx_ret_customer` (`customer_id`),
    KEY `idx_ret_status` (`status`),
    KEY `idx_ret_date` (`return_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售退货单表';

CREATE TABLE IF NOT EXISTS `sal_return_detail` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `return_id`      BIGINT        NOT NULL COMMENT '退货单ID(sal_return.id)',
    `out_detail_id`  BIGINT        NOT NULL COMMENT '来源出库行ID(sal_outstock_detail.id)',
    `material_id`    BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`            DECIMAL(18,4) NOT NULL COMMENT '退货数量(正数)',
    `price`          DECIMAL(18,2) NOT NULL COMMENT '含税单价(取出库行单价)',
    `amount`         DECIMAL(18,2) NOT NULL COMMENT '金额=qty*price 四舍五入2位(正数)',
    `remark`         VARCHAR(200)  DEFAULT NULL COMMENT '行备注',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_retd_return` (`return_id`),
    KEY `idx_retd_out_detail` (`out_detail_id`),
    KEY `idx_retd_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售退货单明细表';
