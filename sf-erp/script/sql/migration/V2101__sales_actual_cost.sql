-- =====================================================================
-- V2101: 跨模块业务接线（主线程集成）—— 单据列扩展
-- 任务来源：采购/销售/生产三期集成（库存统一 StockService / 成本实时归集 /
--           销售实际成本 / 质检卡点 / 审批流三段式）。
-- 版本号说明：任务代号 V2x1；取 2101（必须大于当前已应用的最大版本 962，
--           否则 Flyway 按 outOfOrder=false 不会执行）。
-- 范围（全部幂等：information_schema 判断列存在后再 ALTER，重复执行无害）：
--   1) sal_outstock_detail + actual_cost DECIMAL(18,2) NULL
--      销售出库审核时按 CostService.onStockOut 返回的实际成本回写；
--      NULL=历史数据（改造前出库），统计回退 base_material.ref_cost 估算。
--   2) pur_instock_detail + batch_no VARCHAR(50) NOT NULL DEFAULT ''
--      采购入库批次透传列：表单行可带批次号，审核时经 StockService.stockIn
--      落批次行；空串=非批次物料汇总行（与 inv_stock.batch_no 语义一致）。
--      采购退货审核时按该列反向指定批次出库（BATCH 策略）。
--   3) sal_return_detail + batch_no VARCHAR(50) NOT NULL DEFAULT ''
--      销售退货入库批次透传列（批次物料退货回补需指定批次；空=非批次物料）。
--   4) pur_order / sal_order / mrp_work_order + wf_started TINYINT NOT NULL DEFAULT 0
--      审批流三段式标记：0=从未发起过流程（首次点审核先 startFlow）；
--      1=已发起过（再次点审核时按 isApproved 判定，审批通过即执行原审核；
--      驳回后重新点审核=重新发起新实例）。不引入"审批中"单据状态，
--      单据在流程走完前保持草稿态，前端状态机无需变更。
-- =====================================================================

-- ---------- 1. sal_outstock_detail: 实际成本列 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_outstock_detail' AND COLUMN_NAME = 'actual_cost') = 0,
    'ALTER TABLE sal_outstock_detail ADD COLUMN actual_cost DECIMAL(18,2) NULL COMMENT ''出库实际成本(审核时CostService移动加权计价回写;NULL=历史数据,统计回退ref_cost)'' AFTER amount',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. pur_instock_detail: 入库批次列 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_instock_detail' AND COLUMN_NAME = 'batch_no') = 0,
    'ALTER TABLE pur_instock_detail ADD COLUMN batch_no VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''入库批次号(批次物料必填,空=非批次物料汇总行;退货按此批次反向出库)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. sal_return_detail: 退货入库批次列 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_return_detail' AND COLUMN_NAME = 'batch_no') = 0,
    'ALTER TABLE sal_return_detail ADD COLUMN batch_no VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''退货入库批次号(批次物料回补指定批次,空=非批次物料汇总行)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. pur_order: 审批流发起标记 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pur_order' AND COLUMN_NAME = 'wf_started') = 0,
    'ALTER TABLE pur_order ADD COLUMN wf_started TINYINT NOT NULL DEFAULT 0 COMMENT ''审批流发起标记:0未发起 1已发起(三段式审核依据,见PurOrderServiceImpl)'' AFTER audit_time',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5. sal_order: 审批流发起标记 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sal_order' AND COLUMN_NAME = 'wf_started') = 0,
    'ALTER TABLE sal_order ADD COLUMN wf_started TINYINT NOT NULL DEFAULT 0 COMMENT ''审批流发起标记:0未发起 1已发起(三段式审核依据,见SalOrderServiceImpl)'' AFTER close_time',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 6. mrp_work_order: 审批流发起标记 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_order' AND COLUMN_NAME = 'wf_started') = 0,
    'ALTER TABLE mrp_work_order ADD COLUMN wf_started TINYINT NOT NULL DEFAULT 0 COMMENT ''审批流发起标记:0未发起 1已发起(三段式下达依据,见MrpWorkOrderServiceImpl)'' AFTER bad_qty',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
