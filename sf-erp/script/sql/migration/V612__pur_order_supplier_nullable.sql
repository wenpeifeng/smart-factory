-- =====================================================================
-- V612: pur_order.supplier_id 放开可空（mrp → purchase 转单接线）
-- 背景：
--   MRP 采购建议转采购订单时供应商可能未定（建议本身不携带供应商），
--   需要先生成草稿、由采购员补填供应商/单价后再审核（服务层 audit 前
--   强校验供应商非空）。原列 NOT NULL 无默认值导致草稿无法落库。
-- 口径：
--   * 仅【草稿(0)】允许供应商为空；审核(10+)前应用层强校验；
--   * 存量数据不受影响（全部有供应商）。
-- 幂等：按 information_schema.IS_NULLABLE 判断后再 ALTER，重复执行无害。
-- =====================================================================

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_order'
        AND COLUMN_NAME = 'supplier_id' AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE `pur_order` MODIFY COLUMN `supplier_id` BIGINT NULL COMMENT ''供应商ID(base_supplier.id;草稿可空=MRP转单待补填,审核前必填)''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
