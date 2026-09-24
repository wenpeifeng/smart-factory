-- =====================================================================
-- V941 发票管理模块建表（erp-invoice，V94x 段）
-- 对标金蝶发票管理（增值税电子发票场景，简化版）：
--   1) inv_sales_invoice   销项发票（从销售出库单开票 / 开票 / 红冲）
--   2) inv_purchase_invoice 进项发票（从采购入库单登记 / 认证 / 红冲）
-- 金额约定：金额 DECIMAL(18,2)；税率 DECIMAL(5,2) 存百分数(13.00/9.00/6.00)；
--   税额 = 金额 × 税率 ÷ 100 四舍五入 HALF_UP 2 位；价税合计 = 金额 + 税额。
-- 红冲约定：原蓝字发票状态置为已红冲，同时新插入一条负数红字发票
--   （red_flag=1，source_invoice_id 指向原票）；统计口径下蓝票+红票净额为 0。
-- 统计口径：status <> 0（待开票/待认证不计税）；销项按 invoice_date、
--   进项按 auth_date 归属期间；已红冲蓝票仍计入（由红字负数自然冲减）。
-- =====================================================================

-- ---------------------------- 销项发票 ----------------------------
CREATE TABLE IF NOT EXISTS `inv_sales_invoice` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `invoice_no`         VARCHAR(20)  NOT NULL COMMENT '发票号码(8位数字)',
    `invoice_date`       DATE         DEFAULT NULL COMMENT '开票日期(开票/红冲时写入，待开票为NULL)',
    `customer_id`        BIGINT       NOT NULL COMMENT '购方客户ID(base_customer.id)',
    `biz_type`           VARCHAR(20)  NOT NULL DEFAULT 'SAL_OUTSTOCK' COMMENT '业务单据类型:SAL_OUTSTOCK销售出库',
    `biz_id`             BIGINT       NOT NULL COMMENT '业务单据ID(sal_outstock.id)',
    `biz_no`             VARCHAR(40)  NOT NULL COMMENT '业务单号(sal_outstock.out_no)',
    `amount`             DECIMAL(18, 2) NOT NULL COMMENT '金额(不含税)',
    `tax_rate`           DECIMAL(5, 2) NOT NULL COMMENT '税率%(13.00/9.00/6.00)',
    `tax_amount`         DECIMAL(18, 2) NOT NULL COMMENT '税额=金额×税率% HALF_UP 2位(红字为负)',
    `total_amount`       DECIMAL(18, 2) NOT NULL COMMENT '价税合计=金额+税额(红字为负)',
    `red_flag`           TINYINT      NOT NULL DEFAULT 0 COMMENT '红字标志:0蓝字 1红字(负数)',
    `source_invoice_id`  BIGINT       DEFAULT NULL COMMENT '红字票关联的原蓝字发票ID(蓝字为NULL)',
    `source_invoice_no`  VARCHAR(20)  DEFAULT NULL COMMENT '红字票关联的原蓝字发票号码',
    `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0待开票 10已开票 20已红冲',
    `issue_by`           BIGINT       DEFAULT NULL COMMENT '开票人(开票/红冲操作人)',
    `issue_time`         DATETIME     DEFAULT NULL COMMENT '开票时间',
    `remark`             VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_by`          BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sinv_no` (`invoice_no`),
    KEY `idx_sinv_customer` (`customer_id`),
    KEY `idx_sinv_biz` (`biz_id`),
    KEY `idx_sinv_status` (`status`),
    KEY `idx_sinv_date` (`invoice_date`),
    KEY `idx_sinv_source` (`source_invoice_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='销项发票表';

-- ---------------------------- 进项发票 ----------------------------
CREATE TABLE IF NOT EXISTS `inv_purchase_invoice` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `invoice_no`         VARCHAR(20)  NOT NULL COMMENT '发票号码(8位数字)',
    `invoice_date`       DATE         DEFAULT NULL COMMENT '开票日期(登记时写入)',
    `supplier_id`        BIGINT       NOT NULL COMMENT '销方供应商ID(base_supplier.id)',
    `biz_type`           VARCHAR(20)  NOT NULL DEFAULT 'PUR_INSTOCK' COMMENT '业务单据类型:PUR_INSTOCK采购入库',
    `biz_id`             BIGINT       NOT NULL COMMENT '业务单据ID(pur_instock.id)',
    `biz_no`             VARCHAR(40)  NOT NULL COMMENT '业务单号(pur_instock.instock_no)',
    `amount`             DECIMAL(18, 2) NOT NULL COMMENT '金额(不含税)',
    `tax_rate`           DECIMAL(5, 2) NOT NULL COMMENT '税率%(13.00/9.00/6.00)',
    `tax_amount`         DECIMAL(18, 2) NOT NULL COMMENT '税额=金额×税率% HALF_UP 2位(红字为负)',
    `total_amount`       DECIMAL(18, 2) NOT NULL COMMENT '价税合计=金额+税额(红字为负)',
    `red_flag`           TINYINT      NOT NULL DEFAULT 0 COMMENT '红字标志:0蓝字 1红字(负数)',
    `source_invoice_id`  BIGINT       DEFAULT NULL COMMENT '红字票关联的原蓝字发票ID(蓝字为NULL)',
    `source_invoice_no`  VARCHAR(20)  DEFAULT NULL COMMENT '红字票关联的原蓝字发票号码',
    `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0待认证 10已认证 20已红冲',
    `auth_date`          DATE         DEFAULT NULL COMMENT '认证日期(认证时写入，进项税额归属期间)',
    `auth_by`            BIGINT       DEFAULT NULL COMMENT '认证人',
    `remark`             VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_by`          BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pinv_no` (`invoice_no`),
    KEY `idx_pinv_supplier` (`supplier_id`),
    KEY `idx_pinv_biz` (`biz_id`),
    KEY `idx_pinv_status` (`status`),
    KEY `idx_pinv_date` (`invoice_date`),
    KEY `idx_pinv_auth_date` (`auth_date`),
    KEY `idx_pinv_source` (`source_invoice_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='进项发票表';
