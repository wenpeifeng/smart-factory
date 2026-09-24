-- =====================================================================
-- V422: WMS 库内作业深化（二）—— 效期 FEFO + 序列号追溯
-- 范围（全部幂等：information_schema 判断列/表存在后再执行，重复执行无害）：
--   1) inv_stock + expiry_date DATE NULL
--      批次行效期：入库带效期时写入（行已存在且效期为空则回填），FEFO 出库排序键；
--      上架转移库位行时从来源行复制，保持同批次效期一致。
--   2) base_material + shelf_life_days INT NULL
--      物料保质期天数：入库未显式传效期时自动推算 expiry = 入库日 + shelf_life_days。
--   3) inv_other_in_line + expiry_date DATE NULL
--      其他入库单行透传效期（表单字段 expiryDate，批次物料可填）。
--   3b) inv_other_out + strategy VARCHAR(10) NULL
--      其他出库单出库策略（FIFO 默认 / FEFO 先到期先出 / AVG 总量扣减；
--      行带批次号时该行强制 BATCH 精确扣批次）。
--   4) inv_serial 序列号表：序列号全生命周期（IN_STOCK 在库 / OUT 已出 / SCRAP 报废），
--      入库登记（批量/逐条）→ 出库核验（核销置 OUT）→ 正反查追溯。
-- =====================================================================

-- ---------- 1. inv_stock: 效期列 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'expiry_date') = 0,
    'ALTER TABLE inv_stock ADD COLUMN expiry_date DATE NULL COMMENT ''效期(FEFO出库排序键,批次行随入库写入,上架复制到库位行)'' AFTER first_in_at',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. base_material: 保质期天数 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_material' AND COLUMN_NAME = 'shelf_life_days') = 0,
    'ALTER TABLE base_material ADD COLUMN shelf_life_days INT NULL COMMENT ''保质期天数(入库未传效期时自动推算expiry=入库日+天数)'' AFTER batch_managed',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. inv_other_in_line: 效期透传 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_in_line' AND COLUMN_NAME = 'expiry_date') = 0,
    'ALTER TABLE inv_other_in_line ADD COLUMN expiry_date DATE NULL COMMENT ''效期(批次物料入库时透传,FEFO出库依据)'' AFTER batch_no',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3b. inv_other_out: 出库策略 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_out' AND COLUMN_NAME = 'strategy') = 0,
    'ALTER TABLE inv_other_out ADD COLUMN strategy VARCHAR(10) NULL COMMENT ''出库策略:FIFO默认 FEFO先到期先出 AVG总量扣减(行指定批次时强制BATCH)'' AFTER reason',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. inv_serial 序列号表 ----------
CREATE TABLE IF NOT EXISTS `inv_serial` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=总厂)',
    `tenant_id`    BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `serial_no`    VARCHAR(64)  NOT NULL COMMENT '序列号(全局唯一)',
    `material_id`  BIGINT       NOT NULL COMMENT '物料ID',
    `warehouse_id` BIGINT       NOT NULL COMMENT '当前/最后所在仓库ID',
    `batch_no`     VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '批次号(可空串)',
    `status`       VARCHAR(10)  NOT NULL DEFAULT 'IN_STOCK' COMMENT '状态:IN_STOCK在库 OUT已出库 SCRAP报废',
    `source_no`    VARCHAR(40)  DEFAULT NULL COMMENT '入库来源单号(采购入库/其他入库等)',
    `out_no`       VARCHAR(40)  DEFAULT NULL COMMENT '出库单号(核销时写)',
    `remark`       VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_serial_no` (`serial_no`),
    KEY `idx_serial_material` (`material_id`, `status`),
    KEY `idx_serial_wh` (`warehouse_id`),
    KEY `idx_serial_source_no` (`source_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '序列号库存(IN_STOCK/OUT/SCRAP全生命周期追溯)';
