-- =====================================================================
-- V1203: MES 全链路业务演示数据
--
-- 场景：精密减速机装配
-- 链路：物料/BOM/工艺路线 -> 工单/排程/齐套 -> 领退料 -> 过站/WIP
--       -> 质量/SPC/不合格处置 -> 设备/安灯 -> 批次追溯/EBR -> ERP 回传
--
-- 说明：
--   1. 演示数据随 Flyway 迁移进入代码仓库，新数据库首次启动自动生成；
--   2. 日期使用 CURRENT_DATE/NOW 相对生成，首次启动时驾驶舱近 7/30 天口径可见；
--   3. 使用 1203xxxx 预留 ID 与 DEMO- 编码，INSERT IGNORE 保证补数据不覆盖已有数据；
--   4. 共享主数据 org_id=0，业务数据 org_id=1，tenant_id=1000。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 客商、库位与物料主数据
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `base_supplier`
(`id`, `org_id`, `tenant_id`, `code`, `name`, `contact`, `phone`, `address`, `credit_code`,
 `settle_type_id`, `init_payable`, `status`, `remark`, `create_by`, `create_time`, `deleted`)
VALUES
(12030001, 0, 1000, 'DEMO-SUP-001', '华东精密轴承有限公司', '周工', '13800001001',
 '江苏省苏州市工业园区制造路18号', '91320594DEMO00001A', 4, 0, 1, 'MES演示供应商', 1, NOW(), 0),
(12030002, 0, 1000, 'DEMO-SUP-002', '嘉兴特种钢材有限公司', '沈经理', '13800001002',
 '浙江省嘉兴市南湖区钢材路6号', '91330402DEMO00002B', 4, 0, 1, 'MES演示供应商', 1, NOW(), 0);

INSERT IGNORE INTO `base_customer`
(`id`, `org_id`, `tenant_id`, `code`, `name`, `contact`, `phone`, `address`, `credit_code`,
 `settle_type_id`, `credit_limit`, `init_receivable`, `status`, `remark`, `create_by`, `create_time`, `deleted`)
VALUES
(12030001, 0, 1000, 'DEMO-CUS-001', '长三角智能装备有限公司', '陈经理', '13900001001',
 '上海市嘉定区智能制造路88号', '91310114DEMO00001C', 4, 500000, 0, 1, 'MES演示客户', 1, NOW(), 0);

INSERT IGNORE INTO `base_location`
(`id`, `org_id`, `tenant_id`, `warehouse_id`, `code`, `name`, `capacity`, `status`, `remark`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030001, 0, 1000, 1, 'DEMO-A01', '原料区 A01', 5000, 1, '演示原料库位', 1, NOW(), 0),
(12030002, 0, 1000, 1, 'DEMO-F01', '成品区 F01', 2000, 1, '演示成品库位', 1, NOW(), 0),
(12030003, 0, 1000, 2, 'DEMO-NG01', '不良品隔离区', 500, 1, '演示不良品库位', 1, NOW(), 0);

INSERT IGNORE INTO `base_material`
(`id`, `org_id`, `tenant_id`, `code`, `name`, `spec`, `category_id`, `unit_id`, `barcode`,
 `batch_managed`, `serial_managed`, `safety_stock`, `ref_cost`, `status`, `remark`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030101, 0, 1000, 'DEMO-RM-001', '合金钢传动轴', '40Cr / Φ35×260mm', 1, 1, '6901203010101', 1, 0, 120, 86.50, 1, '减速机核心原料', 1, NOW(), 0),
(12030102, 0, 1000, 'DEMO-RM-002', '深沟球轴承', '6207-2RS', 1, 1, '6901203010200', 1, 1, 240, 32.80, 1, '关键件，按序列号追溯', 1, NOW(), 0),
(12030103, 0, 1000, 'DEMO-RM-003', '铝合金壳体', 'ADC12 / 160型', 1, 1, '6901203010309', 1, 0, 100, 115.00, 1, '压铸壳体', 1, NOW(), 0),
(12030104, 0, 1000, 'DEMO-RM-004', '工业齿轮油', 'ISO VG 220', 1, 4, '6901203010408', 1, 0, 50, 18.00, 1, '装配润滑用', 1, NOW(), 0),
(12030105, 0, 1000, 'DEMO-SF-001', '传动组件', 'CS-25', 3, 1, '6901203010507', 1, 0, 60, 248.00, 1, '减速机半成品', 1, NOW(), 0),
(12030106, 0, 1000, 'DEMO-FG-001', '精密减速机', 'JX-160 / i=25', 2, 1, '6901203010606', 1, 1, 30, 680.00, 1, 'MES全链路演示成品', 1, NOW(), 0);

-- ---------------------------------------------------------------------
-- 2. 工作中心、班组、工序、BOM 与工艺路线
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `base_workcenter`
(`id`, `code`, `name`, `workshop`, `capacity_hours_per_day`, `equipment_count`, `team_id`,
 `status`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030201, 'DEMO-WC-MACH', '精密加工中心', '机加工车间', 16, 2, 12030211, 'ENABLED', '传动轴精加工', 1000, 0, 1, NOW(), 0),
(12030202, 'DEMO-WC-ASSY', '总装工作中心', '装配车间', 16, 3, 12030212, 'ENABLED', '减速机装配与调试', 1000, 0, 1, NOW(), 0),
(12030203, 'DEMO-WC-QC', '终检工作中心', '质量中心', 8, 1, 12030213, 'ENABLED', '成品性能终检', 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_team`
(`id`, `code`, `name`, `leader`, `workcenter_id`, `status`, `remark`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030211, 'DEMO-TEAM-MACH', '机加一班', '王操作', 12030201, 'ENABLED', '演示班组', 1000, 0, 1, NOW(), 0),
(12030212, 'DEMO-TEAM-ASSY', '装配一班', '赵装配', 12030202, 'ENABLED', '演示班组', 1000, 0, 1, NOW(), 0),
(12030213, 'DEMO-TEAM-QC', '质量检验组', '李质检', 12030203, 'ENABLED', '演示班组', 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_team_member`
(`id`, `team_id`, `user_id`, `member_name`, `skill_level`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030211, 12030211, 10, '王操作', 'J3', 1000, 0, 1, NOW(), 0),
(12030212, 12030212, NULL, '赵装配', 'J3', 1000, 0, 1, NOW(), 0),
(12030213, 12030213, 11, '李质检', 'J4', 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_shift_calendar`
(`id`, `team_id`, `work_date`, `shift`, `hours`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030211, 12030211, CURRENT_DATE, 'DAY', 8, 1000, 0, 1, NOW(), 0),
(12030212, 12030212, CURRENT_DATE, 'DAY', 8, 1000, 0, 1, NOW(), 0),
(12030213, 12030213, CURRENT_DATE, 'DAY', 8, 1000, 0, 1, NOW(), 0),
(12030214, 12030211, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), 'DAY', 8, 1000, 0, 1, NOW(), 0),
(12030215, 12030212, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), 'DAY', 8, 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_operation`
(`id`, `code`, `name`, `default_workcenter_id`, `setup_minutes`, `unit_minutes`, `outsource_flag`,
 `qc_flag`, `status`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030221, 'DEMO-OP-010', '传动轴精加工', 12030201, 30, 2.50, 0, 1, 'ENABLED', '关键尺寸过程检验', 1000, 0, 1, NOW(), 0),
(12030222, 'DEMO-OP-020', '减速机总装', 12030202, 20, 3.20, 0, 0, 'ENABLED', '轴承、壳体与传动组件装配', 1000, 0, 1, NOW(), 0),
(12030223, 'DEMO-OP-030', '跑合与终检', 12030203, 15, 1.80, 0, 1, 'ENABLED', '噪声、温升和扭矩终检', 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_bom`
(`id`, `material_id`, `version`, `status`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030231, 12030106, 'V1.0', 'ACTIVE', '精密减速机标准BOM（演示）', 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_bom_item`
(`id`, `bom_id`, `material_id`, `qty`, `loss_rate`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030231, 12030231, 12030101, 1.000000, 2.00, '传动轴', 1000, 0, 1, NOW(), 0),
(12030232, 12030231, 12030102, 2.000000, 1.00, '前后轴承各一套', 1000, 0, 1, NOW(), 0),
(12030233, 12030231, 12030103, 1.000000, 1.00, '铝合金壳体', 1000, 0, 1, NOW(), 0),
(12030234, 12030231, 12030104, 0.350000, 3.00, '齿轮油（千克）', 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_routing`
(`id`, `material_id`, `version`, `status`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030241, 12030106, 'V1.0', 'ACTIVE', '精密减速机标准工艺路线（演示）', 1000, 0, 1, NOW(), 0);

INSERT IGNORE INTO `base_routing_step`
(`id`, `routing_id`, `seq_no`, `operation_id`, `workcenter_id`, `unit_minutes`, `report_flag`,
 `key_component_flag`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030241, 12030241, 10, 12030221, 12030201, 2.50, 1, 0, '精加工并测量轴径', 1000, 0, 1, NOW(), 0),
(12030242, 12030241, 20, 12030222, 12030202, 3.20, 1, 1, '装配时采集轴承SN', 1000, 0, 1, NOW(), 0),
(12030243, 12030241, 30, 12030223, 12030203, 1.80, 1, 0, '跑合与终检', 1000, 0, 1, NOW(), 0);

-- ---------------------------------------------------------------------
-- 3. 工单、用料、工序、排程与齐套分析
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `plan_workorder`
(`id`, `wo_no`, `material_id`, `qty`, `bom_id`, `bom_version`, `routing_id`, `warehouse_id`,
 `plan_start_date`, `plan_end_date`, `status`, `finish_qty`, `scrap_qty`, `sn_mode`,
 `source_type`, `source_no`, `source_id`, `close_remark`, `remark`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030301, 'DEMO-WO-001', 12030106, 120, 12030231, 'V1.0', 12030241, 1,
 DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), 'PRODUCING', 45, 2, 'SN',
 'ERP_ORDER', 'DEMO-MO-001', 12030301, NULL, '正在总装的演示工单', 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(12030302, 'DEMO-WO-002', 12030106, 80, 12030231, 'V1.0', 12030241, 1,
 CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), 'RELEASED', 0, 0, 'SN',
 'ERP_ORDER', 'DEMO-MO-002', 12030302, NULL, '已下达待开工，演示齐套与排程', 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030303, 'DEMO-WO-003', 12030106, 60, 12030231, 'V1.0', 12030241, 1,
 DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), 'FINISHED', 58, 2, 'SN',
 'ERP_ORDER', 'DEMO-MO-003', 12030303, NULL, '已完工，保留质量与追溯数据', 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
(12030304, 'DEMO-WO-004', 12030106, 40, 12030231, 'V1.0', 12030241, 1,
 DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY), 'CLOSED', 40, 0, 'BATCH',
 'MANUAL', 'DEMO-MANUAL-004', NULL, '按计划完成并结案', '已关闭历史演示工单', 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 11 DAY), 0),
(12030305, 'DEMO-WO-005', 12030106, 100, 12030231, 'V1.0', 12030241, 1,
 DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 6 DAY), 'DRAFT', 0, 0, 'SN',
 'MANUAL', 'DEMO-MANUAL-005', NULL, NULL, '草稿工单，可用于演示下达操作', 1000, 1, 1, NOW(), 0);

INSERT IGNORE INTO `plan_wo_material`
(`id`, `workorder_id`, `material_id`, `req_qty`, `issued_qty`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030301, 12030301, 12030101, 122.400000, 122.400000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030302, 12030301, 12030102, 242.400000, 200.000000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030303, 12030301, 12030103, 121.200000, 121.200000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030304, 12030301, 12030104, 43.260000, 43.260000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030305, 12030302, 12030101, 81.600000, 0, 1000, 1, 1, NOW(), 0),
(12030306, 12030302, 12030102, 161.600000, 0, 1000, 1, 1, NOW(), 0),
(12030307, 12030302, 12030103, 80.800000, 0, 1000, 1, 1, NOW(), 0),
(12030308, 12030302, 12030104, 28.840000, 0, 1000, 1, 1, NOW(), 0),
(12030309, 12030303, 12030101, 61.200000, 61.200000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
(12030310, 12030303, 12030102, 121.200000, 121.200000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
(12030311, 12030303, 12030103, 60.600000, 60.600000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
(12030312, 12030303, 12030104, 21.630000, 21.630000, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 0);

INSERT IGNORE INTO `plan_wo_step`
(`id`, `workorder_id`, `seq_no`, `operation_id`, `operation_name`, `workcenter_id`, `unit_minutes`,
 `setup_minutes`, `plan_qty`, `plan_start_time`, `plan_end_time`, `status`, `assignee_id`, `assignee_name`,
 `dispatch_time`, `finish_qty`, `scrap_qty`, `rework_qty`, `actual_minutes`, `qc_flag`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030311, 12030301, 10, 12030221, '传动轴精加工', 12030201, 2.50, 30, 120,
 DATE_SUB(NOW(), INTERVAL 30 HOUR), DATE_SUB(NOW(), INTERVAL 18 HOUR), 'FINISHED', 10, '王操作',
 DATE_SUB(NOW(), INTERVAL 36 HOUR), 118, 2, 0, 330, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(12030312, 12030301, 20, 12030222, '减速机总装', 12030202, 3.20, 20, 120,
 DATE_SUB(NOW(), INTERVAL 18 HOUR), DATE_ADD(NOW(), INTERVAL 8 HOUR), 'PROCESSING', 10, '王操作',
 DATE_SUB(NOW(), INTERVAL 24 HOUR), 45, 0, 1, 190, 0, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(12030313, 12030301, 30, 12030223, '跑合与终检', 12030203, 1.80, 15, 120,
 DATE_ADD(NOW(), INTERVAL 8 HOUR), DATE_ADD(NOW(), INTERVAL 18 HOUR), 'PENDING_DISPATCH', NULL, NULL,
 NULL, 0, 0, 0, 0, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(12030321, 12030302, 10, 12030221, '传动轴精加工', 12030201, 2.50, 30, 80,
 DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 6 HOUR), 'DISPATCHED', 10, '王操作',
 NOW(), 0, 0, 0, 0, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030322, 12030302, 20, 12030222, '减速机总装', 12030202, 3.20, 20, 80,
 DATE_ADD(NOW(), INTERVAL 7 HOUR), DATE_ADD(NOW(), INTERVAL 14 HOUR), 'PENDING_DISPATCH', NULL, NULL,
 NULL, 0, 0, 0, 0, 0, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030323, 12030302, 30, 12030223, '跑合与终检', 12030203, 1.80, 15, 80,
 DATE_ADD(NOW(), INTERVAL 15 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), 'PENDING_DISPATCH', NULL, NULL,
 NULL, 0, 0, 0, 0, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030331, 12030303, 10, 12030221, '传动轴精加工', 12030201, 2.50, 30, 60,
 DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 'FINISHED', 10, '王操作',
 DATE_SUB(NOW(), INTERVAL 6 DAY), 60, 0, 0, 170, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
(12030332, 12030303, 20, 12030222, '减速机总装', 12030202, 3.20, 20, 60,
 DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 'FINISHED', 10, '王操作',
 DATE_SUB(NOW(), INTERVAL 5 DAY), 59, 0, 1, 205, 0, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
(12030333, 12030303, 30, 12030223, '跑合与终检', 12030203, 1.80, 15, 60,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'FINISHED', 11, '李质检',
 DATE_SUB(NOW(), INTERVAL 4 DAY), 58, 2, 0, 126, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
(12030341, 12030304, 10, 12030221, '传动轴精加工', 12030201, 2.50, 30, 40,
 DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 'FINISHED', 10, '王操作',
 DATE_SUB(NOW(), INTERVAL 11 DAY), 40, 0, 0, 115, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 11 DAY), 0),
(12030342, 12030304, 20, 12030222, '减速机总装', 12030202, 3.20, 20, 40,
 DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 'FINISHED', 10, '王操作',
 DATE_SUB(NOW(), INTERVAL 10 DAY), 40, 0, 0, 142, 0, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 11 DAY), 0),
(12030343, 12030304, 30, 12030223, '跑合与终检', 12030203, 1.80, 15, 40,
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 'FINISHED', 11, '李质检',
 DATE_SUB(NOW(), INTERVAL 9 DAY), 40, 0, 0, 86, 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 11 DAY), 0);

INSERT IGNORE INTO `plan_schedule`
(`id`, `wo_step_id`, `workorder_id`, `workcenter_id`, `plan_start_time`, `plan_end_time`, `lock_flag`,
 `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030311, 12030311, 12030301, 12030201, DATE_SUB(NOW(), INTERVAL 30 HOUR), DATE_SUB(NOW(), INTERVAL 18 HOUR), 1, 1000, 1, 1, NOW(), 0),
(12030312, 12030312, 12030301, 12030202, DATE_SUB(NOW(), INTERVAL 18 HOUR), DATE_ADD(NOW(), INTERVAL 8 HOUR), 0, 1000, 1, 1, NOW(), 0),
(12030313, 12030313, 12030301, 12030203, DATE_ADD(NOW(), INTERVAL 8 HOUR), DATE_ADD(NOW(), INTERVAL 18 HOUR), 0, 1000, 1, 1, NOW(), 0),
(12030321, 12030321, 12030302, 12030201, DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 6 HOUR), 0, 1000, 1, 1, NOW(), 0),
(12030322, 12030322, 12030302, 12030202, DATE_ADD(NOW(), INTERVAL 7 HOUR), DATE_ADD(NOW(), INTERVAL 14 HOUR), 0, 1000, 1, 1, NOW(), 0),
(12030323, 12030323, 12030302, 12030203, DATE_ADD(NOW(), INTERVAL 15 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), 0, 1000, 1, 1, NOW(), 0);

INSERT IGNORE INTO `plan_kit_check`
(`id`, `check_no`, `workorder_id`, `wo_no`, `result`, `short_count`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030351, 'DEMO-KIT-001', 12030301, 'DEMO-WO-001', 'PART', 1, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030352, 'DEMO-KIT-002', 12030302, 'DEMO-WO-002', 'FULL', 0, 1000, 1, 1, NOW(), 0);

INSERT IGNORE INTO `plan_kit_check_item`
(`id`, `check_id`, `material_id`, `req_qty`, `available_qty`, `short_qty`, `result`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030351, 12030351, 12030101, 122.400000, 160.000000, 0, 'OK', 1000, 1, 1, NOW(), 0),
(12030352, 12030351, 12030102, 242.400000, 200.000000, 42.400000, 'SHORT', 1000, 1, 1, NOW(), 0),
(12030353, 12030351, 12030103, 121.200000, 150.000000, 0, 'OK', 1000, 1, 1, NOW(), 0),
(12030354, 12030352, 12030101, 81.600000, 160.000000, 0, 'OK', 1000, 1, 1, NOW(), 0),
(12030355, 12030352, 12030102, 161.600000, 200.000000, 0, 'OK', 1000, 1, 1, NOW(), 0),
(12030356, 12030352, 12030103, 80.800000, 150.000000, 0, 'OK', 1000, 1, 1, NOW(), 0);

-- ---------------------------------------------------------------------
-- 4. 设备、报工与在制品
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `eqp_device`
(`id`, `code`, `name`, `model`, `workcenter_id`, `status`, `location`, `buy_date`, `remark`,
 `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030401, 'DEMO-EQP-001', '数控车削中心 01', 'CK6150', 12030201, 'RUNNING', '机加工车间 A1', DATE_SUB(CURRENT_DATE, INTERVAL 900 DAY), '演示设备', 1000, 1, 1, NOW(), 0),
(12030402, 'DEMO-EQP-002', '智能拧紧工作站', 'AT-800', 12030202, 'RUNNING', '装配车间 B2', DATE_SUB(CURRENT_DATE, INTERVAL 540 DAY), '扭矩自动采集', 1000, 1, 1, NOW(), 0),
(12030403, 'DEMO-EQP-003', '减速机跑合台', 'RT-160', 12030203, 'IDLE', '质量中心 C1', DATE_SUB(CURRENT_DATE, INTERVAL 360 DAY), '终检设备', 1000, 1, 1, NOW(), 0);

INSERT IGNORE INTO `exe_station_record`
(`id`, `record_no`, `workorder_id`, `wo_no`, `wo_step_id`, `seq_no`, `operation_id`, `operation_name`,
 `workcenter_id`, `device_id`, `sn`, `key_component_sn`, `good_qty`, `scrap_qty`, `rework_qty`,
 `work_minutes`, `worker_id`, `worker_name`, `team_id`, `station_time`, `result`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030411, 'DEMO-ST-001', 12030301, 'DEMO-WO-001', 12030311, 10, 12030221, '传动轴精加工', 12030201, 12030401,
 'DEMO-BATCH-WO001', NULL, 58, 2, 0, 165, 10, '王操作', 12030211, DATE_SUB(NOW(), INTERVAL 26 HOUR), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 26 HOUR), 0),
(12030412, 'DEMO-ST-002', 12030301, 'DEMO-WO-001', 12030311, 10, 12030221, '传动轴精加工', 12030201, 12030401,
 'DEMO-BATCH-WO001', NULL, 60, 0, 0, 165, 10, '王操作', 12030211, DATE_SUB(NOW(), INTERVAL 20 HOUR), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 20 HOUR), 0),
(12030413, 'DEMO-ST-003', 12030301, 'DEMO-WO-001', 12030312, 20, 12030222, '减速机总装', 12030202, 12030402,
 'DEMO-SN-001', 'DEMO-BEARING-SN-001,DEMO-BEARING-SN-002', 24, 0, 1, 96, 10, '王操作', 12030212, DATE_SUB(NOW(), INTERVAL 10 HOUR), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 10 HOUR), 0),
(12030414, 'DEMO-ST-004', 12030301, 'DEMO-WO-001', 12030312, 20, 12030222, '减速机总装', 12030202, 12030402,
 'DEMO-SN-002', 'DEMO-BEARING-SN-003,DEMO-BEARING-SN-004', 21, 0, 0, 94, 10, '王操作', 12030212, DATE_SUB(NOW(), INTERVAL 2 HOUR), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0),
(12030421, 'DEMO-ST-011', 12030303, 'DEMO-WO-003', 12030331, 10, 12030221, '传动轴精加工', 12030201, 12030401,
 'DEMO-BATCH-WO003', NULL, 60, 0, 0, 170, 10, '王操作', 12030211, DATE_SUB(NOW(), INTERVAL 5 DAY), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
(12030422, 'DEMO-ST-012', 12030303, 'DEMO-WO-003', 12030332, 20, 12030222, '减速机总装', 12030202, 12030402,
 'DEMO-BATCH-WO003', 'DEMO-BEARING-B003-A,DEMO-BEARING-B003-B', 59, 0, 1, 205, 10, '王操作', 12030212, DATE_SUB(NOW(), INTERVAL 4 DAY), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 4 DAY), 0),
(12030423, 'DEMO-ST-013', 12030303, 'DEMO-WO-003', 12030333, 30, 12030223, '跑合与终检', 12030203, 12030403,
 'DEMO-BATCH-WO003', NULL, 58, 2, 0, 126, 11, '李质检', 12030213, DATE_SUB(NOW(), INTERVAL 3 DAY), 'PASS', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
(12030431, 'DEMO-ST-021', 12030304, 'DEMO-WO-004', 12030341, 10, 12030221, '传动轴精加工', 12030201, 12030401,
 'DEMO-BATCH-WO004', NULL, 40, 0, 0, 115, 10, '王操作', 12030211, DATE_SUB(NOW(), INTERVAL 10 DAY), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 10 DAY), 0),
(12030432, 'DEMO-ST-022', 12030304, 'DEMO-WO-004', 12030342, 20, 12030222, '减速机总装', 12030202, 12030402,
 'DEMO-BATCH-WO004', NULL, 40, 0, 0, 142, 10, '王操作', 12030212, DATE_SUB(NOW(), INTERVAL 9 DAY), 'PASS', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 9 DAY), 0),
(12030433, 'DEMO-ST-023', 12030304, 'DEMO-WO-004', 12030343, 30, 12030223, '跑合与终检', 12030203, 12030403,
 'DEMO-BATCH-WO004', NULL, 40, 0, 0, 86, 11, '李质检', 12030213, DATE_SUB(NOW(), INTERVAL 8 DAY), 'PASS', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 8 DAY), 0);

INSERT IGNORE INTO `exe_wip`
(`id`, `workorder_id`, `wo_no`, `wo_step_id`, `seq_no`, `operation_name`, `workcenter_id`, `input_qty`,
 `output_qty`, `wip_qty`, `scrap_qty`, `rework_qty`, `last_station_time`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030411, 12030301, 'DEMO-WO-001', 12030311, 10, '传动轴精加工', 12030201, 120, 118, 0, 2, 0, DATE_SUB(NOW(), INTERVAL 20 HOUR), 1000, 1, 1, NOW(), 0),
(12030412, 12030301, 'DEMO-WO-001', 12030312, 20, '减速机总装', 12030202, 118, 45, 72, 0, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR), 1000, 1, 1, NOW(), 0),
(12030413, 12030301, 'DEMO-WO-001', 12030313, 30, '跑合与终检', 12030203, 45, 0, 45, 0, 0, NULL, 1000, 1, 1, NOW(), 0),
(12030421, 12030303, 'DEMO-WO-003', 12030331, 10, '传动轴精加工', 12030201, 60, 60, 0, 0, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), 1000, 1, 1, NOW(), 0),
(12030422, 12030303, 'DEMO-WO-003', 12030332, 20, '减速机总装', 12030202, 60, 59, 0, 0, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), 1000, 1, 1, NOW(), 0),
(12030423, 12030303, 'DEMO-WO-003', 12030333, 30, '跑合与终检', 12030203, 59, 58, 0, 2, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), 1000, 1, 1, NOW(), 0);

INSERT IGNORE INTO `eqp_check`
(`id`, `check_no`, `device_id`, `checker_name`, `result`, `check_time`, `remark`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030451, 'DEMO-CHK-001', 12030401, '王操作', 'OK', TIMESTAMP(CURRENT_DATE, '07:45:00'), '润滑、刀具与安全门正常', 1000, 1, 10, NOW(), 0),
(12030452, 'DEMO-CHK-002', 12030402, '赵装配', 'OK', TIMESTAMP(CURRENT_DATE, '07:50:00'), '扭矩枪校验通过', 1000, 1, 10, NOW(), 0),
(12030453, 'DEMO-CHK-003', 12030403, '李质检', 'NG', DATE_SUB(NOW(), INTERVAL 1 DAY), '振动传感器连接偶发松动，已提维修', 1000, 1, 11, NOW(), 0);

INSERT IGNORE INTO `eqp_maintain`
(`id`, `maintain_no`, `device_id`, `type`, `plan_date`, `maintainer`, `duration_minutes`, `status`,
 `content`, `finish_time`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030461, 'DEMO-MTN-001', 12030403, 'REPAIR', CURRENT_DATE, '设备工程师', 35, 'DONE', '紧固振动传感器并复测', DATE_SUB(NOW(), INTERVAL 4 HOUR), '复测正常', 1000, 1, 1, NOW(), 0),
(12030462, 'DEMO-MTN-002', 12030401, 'PM', DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), '设备工程师', NULL, 'PLAN', '主轴润滑与刀塔精度点检', NULL, '月度预防保养', 1000, 1, 1, NOW(), 0);

-- ---------------------------------------------------------------------
-- 5. 领退料、线边库存、批次/SN 与标签
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `mat_picking`
(`id`, `pick_no`, `workorder_id`, `wo_no`, `warehouse_id`, `status`, `audit_time`, `auditor`, `pick_type`,
 `remark`, `erp_pushed`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030501, 'DEMO-PICK-001', 12030301, 'DEMO-WO-001', 1, 'AUDITED', DATE_SUB(NOW(), INTERVAL 1 DAY), '赵仓管', 'NORMAL', '生产首批领料', 1, 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(12030502, 'DEMO-PICK-002', 12030302, 'DEMO-WO-002', 1, 'DRAFT', NULL, NULL, 'NORMAL', '待审核领料单，可用于操作演示', 0, 1000, 1, 12, NOW(), 0),
(12030503, 'DEMO-PICK-003', 12030301, 'DEMO-WO-001', 1, 'AUDITED', DATE_SUB(NOW(), INTERVAL 6 HOUR), '赵仓管', 'SUPPLEMENT', '轴承补料', 0, 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 8 HOUR), 0);

INSERT IGNORE INTO `mat_picking_item`
(`id`, `picking_id`, `material_id`, `batch_no`, `qty`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030501, 12030501, 12030101, 'DEMO-RM01-B001', 122.4000, '按BOM领用', 1000, 1, 12, NOW(), 0),
(12030502, 12030501, 12030102, 'DEMO-RM02-B001', 200.0000, '首批轴承', 1000, 1, 12, NOW(), 0),
(12030503, 12030501, 12030103, 'DEMO-RM03-B001', 121.2000, '按BOM领用', 1000, 1, 12, NOW(), 0),
(12030504, 12030501, 12030104, 'DEMO-RM04-B001', 43.2600, '按BOM领用', 1000, 1, 12, NOW(), 0),
(12030505, 12030502, 12030101, 'DEMO-RM01-B002', 81.6000, '待审核', 1000, 1, 12, NOW(), 0),
(12030506, 12030502, 12030102, 'DEMO-RM02-B002', 161.6000, '待审核', 1000, 1, 12, NOW(), 0),
(12030507, 12030503, 12030102, 'DEMO-RM02-B003', 42.4000, '齐套缺口补料', 1000, 1, 12, NOW(), 0);

INSERT IGNORE INTO `mat_return`
(`id`, `return_no`, `workorder_id`, `wo_no`, `return_type`, `warehouse_id`, `status`, `audit_time`, `auditor`,
 `remark`, `erp_pushed`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030511, 'DEMO-RET-001', 12030303, 'DEMO-WO-003', 'LEFTOVER', 1, 'AUDITED', DATE_SUB(NOW(), INTERVAL 2 DAY), '赵仓管', '完工余料退库', 1, 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(12030512, 'DEMO-RET-002', 12030301, 'DEMO-WO-001', 'REJECTED', 2, 'DRAFT', NULL, NULL, '不良轴承待退库', 0, 1000, 1, 12, NOW(), 0);

INSERT IGNORE INTO `mat_return_item`
(`id`, `return_id`, `material_id`, `batch_no`, `qty`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030511, 12030511, 12030104, 'DEMO-RM04-B001', 1.8500, '齿轮油余料', 1000, 1, 12, NOW(), 0),
(12030512, 12030512, 12030102, 'DEMO-RM02-B001', 1.0000, '装配发现游隙超差', 1000, 1, 12, NOW(), 0);

INSERT IGNORE INTO `mat_shop_stock`
(`id`, `material_id`, `batch_no`, `workcenter_id`, `qty`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030521, 12030101, 'DEMO-RM01-B001', 12030201, 62.4000, 1000, 1, 12, NOW(), 0),
(12030522, 12030102, 'DEMO-RM02-B001', 12030202, 109.0000, 1000, 1, 12, NOW(), 0),
(12030523, 12030103, 'DEMO-RM03-B001', 12030202, 76.2000, 1000, 1, 12, NOW(), 0),
(12030524, 12030104, 'DEMO-RM04-B001', 12030202, 26.4100, 1000, 1, 12, NOW(), 0);

INSERT IGNORE INTO `mat_batch_sn`
(`id`, `obj_type`, `obj_no`, `material_id`, `source_type`, `source_no`, `workorder_id`, `qty`,
 `supplier_id`, `status`, `parent_sn`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030531, 'BATCH', 'DEMO-RM01-B001', 12030101, 'ERP_RECEIPT', 'DEMO-GR-001', NULL, 300, 12030002, 'IN_STOCK', NULL, 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 12 DAY), 0),
(12030532, 'BATCH', 'DEMO-RM02-B001', 12030102, 'ERP_RECEIPT', 'DEMO-GR-002', NULL, 500, 12030001, 'IN_STOCK', NULL, 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 10 DAY), 0),
(12030533, 'BATCH', 'DEMO-RM03-B001', 12030103, 'ERP_RECEIPT', 'DEMO-GR-003', NULL, 260, 12030002, 'IN_STOCK', NULL, 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 9 DAY), 0),
(12030534, 'BATCH', 'DEMO-RM04-B001', 12030104, 'ERP_RECEIPT', 'DEMO-GR-004', NULL, 100, 12030001, 'IN_STOCK', NULL, 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 8 DAY), 0),
(12030535, 'SN', 'DEMO-BEARING-SN-001', 12030102, 'ERP_RECEIPT', 'DEMO-GR-002', NULL, 1, 12030001, 'CONSUMED', 'DEMO-SN-001', 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 10 DAY), 0),
(12030536, 'SN', 'DEMO-BEARING-SN-002', 12030102, 'ERP_RECEIPT', 'DEMO-GR-002', NULL, 1, 12030001, 'CONSUMED', 'DEMO-SN-001', 1000, 1, 12, DATE_SUB(NOW(), INTERVAL 10 DAY), 0),
(12030537, 'SN', 'DEMO-SN-001', 12030106, 'PRODUCE', 'DEMO-WO-001', 12030301, 1, NULL, 'IN_STOCK', NULL, 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 10 HOUR), 0),
(12030538, 'BATCH', 'DEMO-BATCH-WO003', 12030106, 'PRODUCE', 'DEMO-WO-003', 12030303, 58, NULL, 'IN_STOCK', NULL, 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 3 DAY), 0);

INSERT IGNORE INTO `mat_label_log`
(`id`, `obj_type`, `obj_id`, `obj_no`, `template`, `copies`, `printer`, `print_time`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030541, 'WO', 12030301, 'DEMO-WO-001', '工单流转卡', 2, '装配车间-ZPL-01', DATE_SUB(NOW(), INTERVAL 1 DAY), 1000, 1, 10, NOW(), 0),
(12030542, 'SN', 12030537, 'DEMO-SN-001', '成品铭牌', 1, '终检-ZPL-01', DATE_SUB(NOW(), INTERVAL 8 HOUR), 1000, 1, 11, NOW(), 0);

-- ---------------------------------------------------------------------
-- 6. 质量检验、SPC、不合格处置与返工
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `qc_template`
(`id`, `template_code`, `template_name`, `category_id`, `inspect_type`, `status`, `remark`,
 `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030601, 'DEMO-QT-IPQC', '传动轴过程检验模板', 1, 'IPQC', 1, '轴径与表面质量', 1000, 0, 11, NOW(), 0),
(12030602, 'DEMO-QT-FQC', '减速机成品终检模板', 2, 'FQC', 1, '噪声、温升与输出扭矩', 1000, 0, 11, NOW(), 0),
(12030603, 'DEMO-QT-IQC', '轴承来料检验模板', 1, 'IQC', 1, '外观、游隙与旋转灵活性', 1000, 0, 11, NOW(), 0);

INSERT IGNORE INTO `qc_template_item`
(`id`, `template_id`, `item_name`, `standard_value`, `lower_limit`, `upper_limit`, `inspect_method`,
 `sample_rate`, `seq`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030601, 12030601, '轴径', '35.00±0.02mm', 34.9800, 35.0200, '千分尺', 20, 1, NULL, 1000, 0, 11, NOW(), 0),
(12030602, 12030601, '表面粗糙度', 'Ra≤0.8μm', NULL, 0.8000, '粗糙度仪', 20, 2, NULL, 1000, 0, 11, NOW(), 0),
(12030603, 12030602, '运行噪声', '≤68dB', NULL, 68.0000, '声级计', 100, 1, NULL, 1000, 0, 11, NOW(), 0),
(12030604, 12030602, '输出扭矩', '≥120N·m', 120.0000, NULL, '扭矩测试台', 100, 2, NULL, 1000, 0, 11, NOW(), 0),
(12030605, 12030603, '旋转灵活性', '转动平稳无卡滞', NULL, NULL, '手感与转矩仪', 10, 1, NULL, 1000, 0, 11, NOW(), 0),
(12030606, 12030603, '径向游隙', '0.015-0.035mm', 0.0150, 0.0350, '游隙仪', 10, 2, NULL, 1000, 0, 11, NOW(), 0);

INSERT IGNORE INTO `qc_inspect`
(`id`, `inspect_no`, `inspect_type`, `source_type`, `source_id`, `source_no`, `template_id`, `workorder_id`,
 `wo_step_id`, `material_id`, `supplier_id`, `batch_no`, `sample_qty`, `qualified_qty`, `unqualified_qty`,
 `conclusion`, `inspector_name`, `inspect_date`, `status`, `submit_time`, `remark`, `tenant_id`, `org_id`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030611, 'DEMO-QC-001', 'IPQC', 'exe_station', 12030411, 'DEMO-ST-001', 12030601, 12030301, 12030311,
 12030101, NULL, 'DEMO-BATCH-WO001', 12, 12, 0, 1, '李质检', DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), 10,
 DATE_SUB(NOW(), INTERVAL 24 HOUR), '过程尺寸稳定', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 25 HOUR), 0),
(12030612, 'DEMO-QC-002', 'FQC', 'plan_workorder', 12030303, 'DEMO-WO-003', 12030602, 12030303, 12030333,
 12030106, NULL, 'DEMO-BATCH-WO003', 60, 58, 2, 3, '李质检', DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY), 10,
 DATE_SUB(NOW(), INTERVAL 3 DAY), '2台噪声超限，判定返工', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
(12030613, 'DEMO-QC-003', 'IQC', 'mat_receipt', 12030532, 'DEMO-GR-002', 12030603, NULL, NULL,
 12030102, 12030001, 'DEMO-RM02-B001', 50, 49, 1, 2, '李质检', DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY), 10,
 DATE_SUB(NOW(), INTERVAL 7 DAY), '1件游隙临界，让步接收用于非关键样机', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 7 DAY), 0),
(12030614, 'DEMO-QC-004', 'IPQC', 'exe_station', 12030421, 'DEMO-ST-011', 12030601, 12030303, 12030331,
 12030101, NULL, 'DEMO-BATCH-WO003', 12, 12, 0, 1, '李质检', DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), 10,
 DATE_SUB(NOW(), INTERVAL 5 DAY), '过程检验合格', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
(12030615, 'DEMO-QC-005', 'FQC', 'plan_workorder', 12030304, 'DEMO-WO-004', 12030602, 12030304, 12030343,
 12030106, NULL, 'DEMO-BATCH-WO004', 40, 40, 0, 1, '李质检', DATE_SUB(CURRENT_DATE, INTERVAL 8 DAY), 10,
 DATE_SUB(NOW(), INTERVAL 8 DAY), '成品终检合格', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 8 DAY), 0),
(12030616, 'DEMO-QC-006', 'IPQC', 'exe_station', 12030412, 'DEMO-ST-002', 12030601, 12030301, 12030311,
 12030101, NULL, 'DEMO-BATCH-WO001', 12, 12, 0, 1, '李质检', CURRENT_DATE, 10,
 DATE_SUB(NOW(), INTERVAL 18 HOUR), '复测合格', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 19 HOUR), 0);

INSERT IGNORE INTO `qc_inspect_item`
(`id`, `inspect_id`, `item_name`, `standard_value`, `lower_limit`, `upper_limit`, `inspect_method`, `sample_rate`,
 `actual_value`, `line_result`, `seq`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030611, 12030611, '轴径', '35.00±0.02mm', 34.9800, 35.0200, '千分尺', 20, '35.006', 1, 1, NULL, 1000, 1, 11, NOW(), 0),
(12030612, 12030611, '表面粗糙度', 'Ra≤0.8μm', NULL, 0.8000, '粗糙度仪', 20, '0.62', 1, 2, NULL, 1000, 1, 11, NOW(), 0),
(12030613, 12030612, '运行噪声', '≤68dB', NULL, 68.0000, '声级计', 100, '69.4', 0, 1, '2台样机超限', 1000, 1, 11, NOW(), 0),
(12030614, 12030612, '输出扭矩', '≥120N·m', 120.0000, NULL, '扭矩测试台', 100, '126.8', 1, 2, NULL, 1000, 1, 11, NOW(), 0),
(12030615, 12030613, '旋转灵活性', '转动平稳无卡滞', NULL, NULL, '手感与转矩仪', 10, '合格', 1, 1, NULL, 1000, 1, 11, NOW(), 0),
(12030616, 12030613, '径向游隙', '0.015-0.035mm', 0.0150, 0.0350, '游隙仪', 10, '0.036', 0, 2, '1件临界超差', 1000, 1, 11, NOW(), 0),
(12030617, 12030614, '轴径', '35.00±0.02mm', 34.9800, 35.0200, '千分尺', 20, '34.998', 1, 1, NULL, 1000, 1, 11, NOW(), 0),
(12030618, 12030615, '运行噪声', '≤68dB', NULL, 68.0000, '声级计', 100, '65.8', 1, 1, NULL, 1000, 1, 11, NOW(), 0),
(12030619, 12030616, '轴径', '35.00±0.02mm', 34.9800, 35.0200, '千分尺', 20, '35.014', 1, 1, NULL, 1000, 1, 11, NOW(), 0);

INSERT IGNORE INTO `qc_reject`
(`id`, `reject_no`, `inspect_id`, `inspect_no`, `material_id`, `qty`, `decision`, `dispose_remark`, `approver`,
 `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030621, 'DEMO-RJ-001', 12030612, 'DEMO-QC-002', 12030106, 2, 2, '拆检轴承预紧并重新跑合', '质量经理', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
(12030622, 'DEMO-RJ-002', 12030613, 'DEMO-QC-003', 12030102, 1, 3, '限用于研发样机，不进入量产订单', '质量经理', 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 7 DAY), 0);

INSERT IGNORE INTO `exe_rework_scrap`
(`id`, `order_no`, `type`, `workorder_id`, `wo_no`, `wo_step_id`, `operation_name`, `sn`, `qty`, `reason`,
 `qc_reject_id`, `status`, `handle_remark`, `handle_time`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030631, 'DEMO-RW-001', 'REWORK', 12030303, 'DEMO-WO-003', 12030333, '跑合与终检', 'DEMO-BATCH-WO003', 2,
 '终检运行噪声超过68dB', 12030621, 'DONE', '重新调整轴承预紧后复测合格', DATE_SUB(NOW(), INTERVAL 2 DAY), 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
(12030632, 'DEMO-SC-001', 'SCRAP', 12030301, 'DEMO-WO-001', 12030311, '传动轴精加工', 'DEMO-BATCH-WO001', 2,
 '轴径尺寸超下限且无法返修', NULL, 'OPEN', NULL, NULL, 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 1 DAY), 0);

INSERT IGNORE INTO `qc_spc_rule`
(`id`, `material_id`, `item_name`, `ucl`, `lcl`, `target`, `rule_r1`, `rule_r2`, `rule_r3`, `rule_r4`, `rule_r5`,
 `status`, `remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030641, 12030101, '轴径', 35.0200, 34.9800, 35.0000, 1, 1, 1, 0, 1, 1, '传动轴关键尺寸SPC', 1000, 1, 11, NOW(), 0),
(12030642, 12030106, '运行噪声', 68.0000, 55.0000, 62.0000, 1, 1, 1, 0, 1, 1, '成品噪声SPC', 1000, 1, 11, NOW(), 0);

INSERT IGNORE INTO `qc_spc_sample`
(`id`, `rule_id`, `inspect_id`, `value`, `sample_time`, `violations`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030641, 12030641, 12030611, 35.0060, DATE_SUB(NOW(), INTERVAL 24 HOUR), NULL, 1000, 1, 11, NOW(), 0),
(12030642, 12030641, 12030614, 34.9980, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 1000, 1, 11, NOW(), 0),
(12030643, 12030641, 12030616, 35.0140, DATE_SUB(NOW(), INTERVAL 18 HOUR), NULL, 1000, 1, 11, NOW(), 0),
(12030644, 12030642, 12030612, 69.4000, DATE_SUB(NOW(), INTERVAL 3 DAY), 'R1', 1000, 1, 11, NOW(), 0),
(12030645, 12030642, 12030615, 65.8000, DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, 1000, 1, 11, NOW(), 0);

-- ---------------------------------------------------------------------
-- 7. 安灯、追溯、电子批记录与 ERP 回传
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `kb_andon`
(`id`, `call_no`, `workcenter_id`, `device_id`, `type`, `caller_name`, `message`, `status`, `call_time`,
 `respond_time`, `responder`, `close_time`, `close_remark`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030701, 'DEMO-AD-001', 12030202, 12030402, 'MATERIAL', '王操作', '总装工位轴承余量低于下一批需求，请及时补料', 'OPEN', DATE_SUB(NOW(), INTERVAL 25 MINUTE),
 NULL, NULL, NULL, NULL, 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 25 MINUTE), 0),
(12030702, 'DEMO-AD-002', 12030203, 12030403, 'DEVICE', '李质检', '跑合台振动传感器信号间歇丢失', 'RESPOND', DATE_SUB(NOW(), INTERVAL 5 HOUR),
 DATE_SUB(NOW(), INTERVAL 4 HOUR), '设备工程师', NULL, NULL, 1000, 1, 11, DATE_SUB(NOW(), INTERVAL 5 HOUR), 0),
(12030703, 'DEMO-AD-003', 12030201, 12030401, 'QUALITY', '王操作', '首件轴径波动接近控制上限，请质量复核', 'CLOSED', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 23 HOUR), '李质检', DATE_SUB(NOW(), INTERVAL 22 HOUR), '复测正常，调整刀补后继续生产', 1000, 1, 10, DATE_SUB(NOW(), INTERVAL 1 DAY), 0);

INSERT IGNORE INTO `trace_link`
(`id`, `product_no`, `workorder_id`, `wo_step_id`, `component_no`, `component_type`, `material_id`, `qty`,
 `link_time`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030711, 'DEMO-SN-001', 12030301, 12030312, 'DEMO-RM01-B001', 'BATCH', 12030101, 1, DATE_SUB(NOW(), INTERVAL 10 HOUR), 1000, 1, 10, NOW(), 0),
(12030712, 'DEMO-SN-001', 12030301, 12030312, 'DEMO-BEARING-SN-001', 'SN', 12030102, 1, DATE_SUB(NOW(), INTERVAL 10 HOUR), 1000, 1, 10, NOW(), 0),
(12030713, 'DEMO-SN-001', 12030301, 12030312, 'DEMO-BEARING-SN-002', 'SN', 12030102, 1, DATE_SUB(NOW(), INTERVAL 10 HOUR), 1000, 1, 10, NOW(), 0),
(12030714, 'DEMO-SN-001', 12030301, 12030312, 'DEMO-RM03-B001', 'BATCH', 12030103, 1, DATE_SUB(NOW(), INTERVAL 10 HOUR), 1000, 1, 10, NOW(), 0),
(12030715, 'DEMO-BATCH-WO003', 12030303, 12030332, 'DEMO-RM02-B001', 'BATCH', 12030102, 121.2, DATE_SUB(NOW(), INTERVAL 4 DAY), 1000, 1, 10, NOW(), 0);

INSERT IGNORE INTO `trace_ebr`
(`id`, `ebr_no`, `workorder_id`, `wo_no`, `material_id`, `batch_no`, `content`, `generate_time`,
 `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030721, 'DEMO-EBR-001', 12030303, 'DEMO-WO-003', 12030106, 'DEMO-BATCH-WO003',
 JSON_OBJECT(
   'scenario', '精密减速机装配演示',
   'routingVersion', 'V1.0',
   'planQty', 60,
   'goodQty', 58,
   'scrapQty', 2,
   'operations', JSON_ARRAY('传动轴精加工', '减速机总装', '跑合与终检'),
   'materialBatches', JSON_ARRAY('DEMO-RM01-B001', 'DEMO-RM02-B001', 'DEMO-RM03-B001'),
   'qualityConclusion', '2台噪声超限返工后复测合格'
 ), DATE_SUB(NOW(), INTERVAL 2 DAY), 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 0);

INSERT IGNORE INTO `int_push_log`
(`id`, `biz_type`, `biz_id`, `biz_no`, `payload`, `status`, `retry_count`, `max_retry`, `last_error`,
 `push_time`, `success_time`, `tenant_id`, `org_id`, `create_by`, `create_time`, `deleted`)
VALUES
(12030731, 'WORK_REPORT', 12030303, 'DEMO-WO-003', JSON_OBJECT('woNo', 'DEMO-WO-003', 'goodQty', 58, 'scrapQty', 2),
 'SUCCESS', 0, 5, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(12030732, 'PICKING', 12030501, 'DEMO-PICK-001', JSON_OBJECT('pickNo', 'DEMO-PICK-001', 'woNo', 'DEMO-WO-001', 'status', 'AUDITED'),
 'SUCCESS', 0, 5, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(12030733, 'RETURN', 12030512, 'DEMO-RET-002', JSON_OBJECT('returnNo', 'DEMO-RET-002', 'woNo', 'DEMO-WO-001', 'qty', 1),
 'PENDING', 0, 5, NULL, NULL, NULL, 1000, 1, 1, NOW(), 0),
(12030734, 'QUALITY', 12030612, 'DEMO-QC-002', JSON_OBJECT('inspectNo', 'DEMO-QC-002', 'conclusion', 3, 'unqualifiedQty', 2),
 'FAILED', 2, 5, 'ERP演示接口暂不可用，等待自动重试', DATE_SUB(NOW(), INTERVAL 30 MINUTE), NULL, 1000, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 0);

-- ---------------------------------------------------------------------
-- 8. 审批中心演示：质量异常处置流程
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `wf_definition`
(`id`, `org_id`, `tenant_id`, `flow_code`, `flow_name`, `biz_type`, `status`, `remark`,
 `create_by`, `create_time`, `deleted`)
VALUES
(12030801, 0, 1000, 'DEMO_QUALITY_EXCEPTION', '质量异常处置审批（演示）', 'QUALITY_REJECT', 1,
 '用于演示质量不合格处置的审批链路', 1, NOW(), 0);

INSERT IGNORE INTO `wf_node`
(`id`, `org_id`, `tenant_id`, `definition_id`, `node_order`, `node_name`, `approver_type`, `approver_id`,
 `condition_expr`, `create_by`, `create_time`, `deleted`)
VALUES
(12030801, 0, 1000, 12030801, 1, '质量主管审批', 'USER', 13, NULL, 1, NOW(), 0),
(12030802, 0, 1000, 12030801, 2, '生产经理确认', 'USER', 13, 'decision == 2', 1, NOW(), 0);

INSERT IGNORE INTO `wf_instance`
(`id`, `org_id`, `tenant_id`, `definition_id`, `biz_type`, `biz_id`, `biz_no`, `status`, `start_user_id`,
 `vars`, `end_time`, `create_by`, `create_time`, `deleted`)
VALUES
(12030801, 0, 1000, 12030801, 'QUALITY_REJECT', 12030621, 'DEMO-RJ-001', 'RUNNING', 11,
 '{"decision":2,"qty":2,"reason":"终检运行噪声超限"}', NULL, 11, DATE_SUB(NOW(), INTERVAL 3 HOUR), 0);

INSERT IGNORE INTO `wf_task`
(`id`, `org_id`, `tenant_id`, `instance_id`, `node_id`, `node_order`, `node_name`, `approver_id`, `status`,
 `opinion`, `approve_time`, `create_by`, `create_time`, `deleted`)
VALUES
(12030801, 0, 1000, 12030801, 12030801, 1, '质量主管审批', 13, 'APPROVED', '同意返工并复测', DATE_SUB(NOW(), INTERVAL 2 HOUR), 11, DATE_SUB(NOW(), INTERVAL 3 HOUR), 0),
(12030802, 0, 1000, 12030801, 12030802, 2, '生产经理确认', 13, 'PENDING', NULL, NULL, 11, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0);

INSERT IGNORE INTO `wf_message`
(`id`, `org_id`, `tenant_id`, `receiver_id`, `msg_type`, `title`, `content`, `biz_path`, `read_flag`,
 `read_time`, `create_by`, `create_time`, `deleted`)
VALUES
(12030801, 0, 1000, 13, 'TODO', '待确认：质量异常返工方案', 'DEMO-RJ-001 已完成质量主管审批，请确认返工方案。',
 '/workflow/tasks', 0, NULL, 11, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0);
