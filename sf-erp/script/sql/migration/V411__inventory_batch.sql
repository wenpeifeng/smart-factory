-- =====================================================================
-- V411: 库存二期扩展 —— 批次管理（批次维度入库 / FIFO·批次指定出库 / 批次追溯）
-- 范围（全部幂等：information_schema 判断列/索引存在后再 ALTER，重复执行无害）：
--   1) inv_stock        + batch_no VARCHAR(50) NOT NULL DEFAULT ''
--                      + first_in_at DATETIME NULL（该批次行首次入库时间，FIFO 排序键）
--                      唯一键 uk_stock(warehouse_id, material_id, deleted)
--                        → uk_stock(warehouse_id, material_id, batch_no, deleted)
--      迁移语义：已有数据 batch_no 默认 ''，新唯一键对旧数据等价于原唯一键
--      （一仓一料至多一行 '' 批次行），旧数据无需回填、不受影响。
--   2) inv_movement     + batch_no VARCHAR(50) NOT NULL DEFAULT ''
--      FIFO 拆批出库时每个批次一行流水，流水按批次可追溯；
--      一期历史流水与采购/销售裸 SQL 写入的流水 batch_no=''（默认值）不受影响。
--   3) inv_other_in_line / inv_other_out_line + batch_no（单据行可指定批次）
--   4) inv_check_detail + batch_no
--      唯一键 uk_check_material(check_id, material_id, deleted)
--        → uk_check_material(check_id, material_id, batch_no, deleted)
--      原因：批次物料同仓多批次行，快照 INSERT...SELECT 每批次一行，
--      不重建唯一键会撞键（明细行的 batch_no 与库存行一一对应）。
-- 兼容性约定（重要，详见 docs/modules/inventory.md 二期章节）：
--   * 一期库存行 batch_no=''；采购/销售模块裸 SQL 的
--     UPDATE/INSERT ... WHERE warehouse_id+material_id 仍命中 batch_no='' 行，
--     对【非批次物料】语义完全不变；
--   * 【批次物料】的出入库必须走 StockService 批次接口（stockIn/stockOut），
--     裸 SQL 对批次物料会造成多行命中/汇总行不存在，禁止使用。
-- =====================================================================

-- ---------- 1. inv_stock: 批次列 + 首次入库时间列 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'batch_no') = 0,
    'ALTER TABLE inv_stock ADD COLUMN batch_no VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''批次号(非批次物料为空串,批次物料必填)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'first_in_at') = 0,
    'ALTER TABLE inv_stock ADD COLUMN first_in_at DATETIME NULL COMMENT ''该批次行首次入库时间(FIFO扣减排序键,仅插入时写入)'' AFTER batch_no',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. inv_stock: 唯一键重建（加 batch_no 维度） ----------
-- 仅当现 uk_stock 仍是三列旧结构时执行，避免重复执行报 Duplicate key name
SET @ddl = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND INDEX_NAME = 'uk_stock')
        = 'warehouse_id,material_id,deleted',
    'ALTER TABLE inv_stock DROP INDEX uk_stock, ADD UNIQUE KEY uk_stock (warehouse_id, material_id, batch_no, deleted)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. inv_movement: 流水批次列（FIFO 拆批出库每行流水记批次） ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_movement' AND COLUMN_NAME = 'batch_no') = 0,
    'ALTER TABLE inv_movement ADD COLUMN batch_no VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''批次号(非批次物料/一期历史流水为空串)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. 其他入库/出库单明细行: 批次列 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_in_line' AND COLUMN_NAME = 'batch_no') = 0,
    'ALTER TABLE inv_other_in_line ADD COLUMN batch_no VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''批次号(批次物料必填,非批次物料空串)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_other_out_line' AND COLUMN_NAME = 'batch_no') = 0,
    'ALTER TABLE inv_other_out_line ADD COLUMN batch_no VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''批次号(指定批次出库时必填,空=FIFO自动分配)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5. 盘点明细: 批次列 + 唯一键重建（快照每批次一行） ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check_detail' AND COLUMN_NAME = 'batch_no') = 0,
    'ALTER TABLE inv_check_detail ADD COLUMN batch_no VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''批次号(快照自inv_stock.batch_no)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check_detail' AND INDEX_NAME = 'uk_check_material')
        = 'check_id,material_id,deleted',
    'ALTER TABLE inv_check_detail DROP INDEX uk_check_material, ADD UNIQUE KEY uk_check_material (check_id, material_id, batch_no, deleted)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
