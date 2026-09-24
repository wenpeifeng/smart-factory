-- =====================================================================
-- V1201__demo_data.sql  演示数据集(苏州精工机械有限公司)
-- =====================================================================
-- 剧本说明
-- ---------------------------------------------------------------------
-- 企业背景: 苏州精工机械有限公司(虚构), 精密齿轮箱/传动轴/联轴器制造企业。
--   组织: 沿用 org_factory(总厂/苏州工厂/东莞工厂); 新增部门10个(总裁办/采购部/
--         销售部/生产部[计划课/车间一/车间二]/仓储部/质检部/人事部; 财务部沿用);
--   人员: 7个演示账号(密码统一 Admin@123): zhanggm 张国民(总经理,超管+管理层角色)/
--         wangcg 王大成(采购)/liuxs 刘晓松(销售)/zhouchs 周建国(车间)/malin 马琳(会计)/
--         hucang 胡国富(仓管)/qizj 齐敏(质检); 另建 management 管理层角色(菜单同超管);
--   物料: 28个(DEMO-WL 前缀) = 原材料12(含4个批次管理)+外协部件3+成品8(3系列)+包装5;
--   客商: 客户8家(信用额度10万~100万)/供应商6家(钢板/铝/轴承/标准件/铜材/铸锻外协);
--   制造: BOM 3张(GB-40三级:成品-部件-原材料 / 箱体、轴组子BOM / CS-25两级) +
--         工艺路线2条(4工序/3工序, 挂3个工作中心7道工序) + MRP运算1次(2条MPS,
--         4条建议, 2条已转9月工单);
--   业务期间: 2026-04 ~ 2026-09-05 六个月完整闭环:
--     采购: 24张订单(2张分批入库/1张在途/2张待审批草稿) + 25张入库 + 1张退货(IQC联动);
--     销售: 30张订单 + 28张出库(4-6月爬坡/7月低谷/8-9月冲高) + 2张退货(红字应收);
--     生产: 12张工单(11张完工/1张部分完工, 含报工与工序任务) + 领料12张 + 委外1张;
--     质量: IQC 4张(1让步接收/1不合格退货) + FQC 4张(1让步接收/1不合格报废) + 处置2张;
--     库存: 期初其他入库铺垫(2/27+3/28) + 盘点1次(差异ADJUST调平) + 仓库调拨1次
--           (主仓库→成品仓, 新增成品仓CK-DEMO-CP) + 其他出库5张;
--     财务: 应付26(付款核销14) / 应收30(收款核销19, 含2笔红字) / 账户流水33 +
--           凭证29(月度收入成本结转+费用2张/月+资产入账3+折旧2期) + 资产3台折旧2期 +
--           预算方案(4-9月24条明细, 14条执行数);
--     发票: 销项5张(13%已开票) + 进项4张(已认证);
--     审批: 采购订单审批流定义 + 2条待办实例(驾驶舱待办数=2)。
-- 一致性保证(生成期断言 + 演练库SQL复核):
--   1) inv_stock.qty = Σ inv_movement.qty (仓库×物料×批次), 时序无负库存;
--   2) cost_flow 逐行 balance_qty/balance_cost 连续, amount=qty×unit_cost;
--   3) 应收应付 settled_amount ≤ amount(红字为负金额), 状态自洽;
--   4) 凭证借贷平衡; 账户流水 balance_after 自500,000连续不透支;
--   5) 全部主键固定在 520,000,000~529,999,999 区间, 不与存量数据冲突。
-- 幂等性: 每块 SET @g := COUNT(该表 id 在演示区间), 已存在则整块跳过, 可安全重跑;
--         演示单号日期均嵌入 202602~202609, 与一期验证残留数据(DEMO/验证前缀)互不干扰。
-- =====================================================================

SET NAMES utf8mb4;


-- ---- sys_dept : 部门(苏州精工组织架构) (10行) ----
SET @g := (SELECT COUNT(*) FROM `sys_dept` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sys_dept` (`id`, `org_id`, `tenant_id`, `parent_id`, `name`, `sort`, `leader`, `phone`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `parent_id`, `name`, `sort`, `leader`, `phone`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520000401 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 1 AS `parent_id`, '总裁办' AS `name`, 1 AS `sort`, '张国民' AS `leader`, '13915400001' AS `phone`, 1 AS `status`, 520000505 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520000402, 0, 1000, 1, '采购部', 2, '王大成', '13915400002', 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000403, 0, 1000, 1, '销售部', 3, '刘晓松', '13915400003', 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000404, 0, 1000, 1, '生产部', 4, '周建国', '13915400004', 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000405, 0, 1000, 520000404, '计划课', 1, '周建国', NULL, 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000406, 0, 1000, 520000404, '车间一', 2, '周建国', NULL, 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000407, 0, 1000, 520000404, '车间二', 3, '吴海', NULL, 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000408, 0, 1000, 1, '仓储部', 5, '胡国富', '13915400006', 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000409, 0, 1000, 1, '质检部', 6, '齐敏', '13915400007', 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000410, 0, 1000, 1, '人事部', 7, '马琳', '13915400005', 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- sys_user : 用户(演示账号 Admin@123) (7行) ----
SET @g := (SELECT COUNT(*) FROM `sys_user` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sys_user` (`id`, `org_id`, `data_scope`, `tenant_id`, `username`, `nickname`, `password`, `phone`, `email`, `dept_id`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `data_scope`, `tenant_id`, `username`, `nickname`, `password`, `phone`, `email`, `dept_id`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520000501 AS `id`, 1 AS `org_id`, 'ALL' AS `data_scope`, 1000 AS `tenant_id`, 'zhanggm' AS `username`, '张国民' AS `nickname`, '$2a$10$HrHuj0bdCnpRCIk1LpVjN.sQ6VW2T157iLWzX4KFK2IFv9d/7yliy' AS `password`, '13915400001' AS `phone`, 'zhanggm@szjinggong.com' AS `email`, 520000401 AS `dept_id`, 1 AS `status`, '总经理(演示)' AS `remark`, 520000505 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520000502, 1, 'ALL', 1000, 'wangcg', '王大成', '$2a$10$HrHuj0bdCnpRCIk1LpVjN.sQ6VW2T157iLWzX4KFK2IFv9d/7yliy', '13915400002', 'wangcg@szjinggong.com', 520000402, 1, '采购经理(演示)', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000503, 1, 'ALL', 1000, 'liuxs', '刘晓松', '$2a$10$HrHuj0bdCnpRCIk1LpVjN.sQ6VW2T157iLWzX4KFK2IFv9d/7yliy', '13915400003', 'liuxs@szjinggong.com', 520000403, 1, '销售经理(演示)', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000504, 1, 'ALL', 1000, 'zhouchs', '周建国', '$2a$10$HrHuj0bdCnpRCIk1LpVjN.sQ6VW2T157iLWzX4KFK2IFv9d/7yliy', '13915400004', 'zhouchs@szjinggong.com', 520000406, 1, '车间主任(演示)', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000505, 1, 'ALL', 1000, 'malin', '马琳', '$2a$10$HrHuj0bdCnpRCIk1LpVjN.sQ6VW2T157iLWzX4KFK2IFv9d/7yliy', '13915400005', 'malin@szjinggong.com', 3, 1, '会计主管(演示)', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000506, 1, 'ALL', 1000, 'hucang', '胡国富', '$2a$10$HrHuj0bdCnpRCIk1LpVjN.sQ6VW2T157iLWzX4KFK2IFv9d/7yliy', '13915400006', 'hucang@szjinggong.com', 520000408, 1, '仓管组长(演示)', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520000507, 1, 'ALL', 1000, 'qizj', '齐敏', '$2a$10$HrHuj0bdCnpRCIk1LpVjN.sQ6VW2T157iLWzX4KFK2IFv9d/7yliy', '13915400007', 'qizj@szjinggong.com', 520000409, 1, '质检主管(演示)', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- sys_role : 角色-管理层 (1行) ----
SET @g := (SELECT COUNT(*) FROM `sys_role` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sys_role` (`id`, `org_id`, `tenant_id`, `name`, `code`, `sort`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `name`, `code`, `sort`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520000510 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, '管理层' AS `name`, 'management' AS `code`, 90 AS `sort`, 1 AS `status`, '演示管理层角色(菜单同超管)' AS `remark`, 520000505 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- sys_user_role : 用户-角色 (2行) ----
SET @g := (SELECT COUNT(*) FROM `sys_user_role` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sys_user_role` (`id`, `org_id`, `tenant_id`, `user_id`, `role_id`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `user_id`, `role_id`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520183001 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 520000501 AS `user_id`, 520000510 AS `role_id`, 520000505 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520183002, 0, 1000, 520000501, 1, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- base_warehouse : 演示成品仓 (1行) ----
SET @g := (SELECT COUNT(*) FROM `base_warehouse` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `base_warehouse` (`id`, `org_id`, `tenant_id`, `factory_id`, `code`, `name`, `manager`, `address`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `factory_id`, `code`, `name`, `manager`, `address`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520010301 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1 AS `factory_id`, 'CK-DEMO-CP' AS `code`, '成品仓' AS `name`, '胡国富' AS `manager`, '总厂一号库北侧' AS `address`, 1 AS `status`, '演示成品仓(调拨目的地)' AS `remark`, 520000505 AS `create_by`, '2026-03-28 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-28 09:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- base_material : 物料(28个 DEMO-WL) (28行) ----
SET @g := (SELECT COUNT(*) FROM `base_material` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `base_material` (`id`, `org_id`, `tenant_id`, `code`, `name`, `spec`, `category_id`, `unit_id`, `barcode`, `batch_managed`, `serial_managed`, `safety_stock`, `ref_cost`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `code`, `name`, `spec`, `category_id`, `unit_id`, `barcode`, `batch_managed`, `serial_managed`, `safety_stock`, `ref_cost`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520000101 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 'DEMO-WL-0101' AS `code`, '冷轧钢板 2.0' AS `name`, 'Q235B 2.0×1250×2500mm(千克)' AS `spec`, 1 AS `category_id`, 1 AS `unit_id`, NULL AS `barcode`, 0 AS `batch_managed`, 0 AS `serial_managed`, 2000 AS `safety_stock`, 4.55 AS `ref_cost`, 1 AS `status`, '框架协议按千克计价' AS `remark`, 520000505 AS `create_by`, '2026-03-28 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-28 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520000102, 0, 1000, 'DEMO-WL-0102', '冷轧钢板 1.5', 'Q235B 1.5×1000×2000mm(千克)', 1, 1, NULL, 0, 0, 1500, 4.35, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000103, 0, 1000, 'DEMO-WL-0103', '铝板 5052', '5052-H32 3.0×1200×2400mm(千克)', 1, 1, NULL, 0, 0, 800, 18.20, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000104, 0, 1000, 'DEMO-WL-0104', '紫铜棒 T2', 'T2 Φ20mm(千克)', 1, 1, NULL, 1, 0, 200, 57.00, 1, '批次管理', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000105, 0, 1000, 'DEMO-WL-0105', '六角螺栓 M6×20', '8.8级 镀锌(个)', 1, 1, NULL, 0, 0, 5000, 0.17, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000106, 0, 1000, 'DEMO-WL-0106', '六角螺栓 M8×25', '8.8级 镀锌(个)', 1, 1, NULL, 0, 0, 30000, 0.25, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000107, 0, 1000, 'DEMO-WL-0107', '六角螺栓 M10×30', '8.8级 镀锌(个)', 1, 1, NULL, 0, 0, 4000, 0.33, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000108, 0, 1000, 'DEMO-WL-0108', '六角螺栓 M12×35', '8.8级 镀锌(个)', 1, 1, NULL, 0, 0, 3000, 0.50, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000109, 0, 1000, 'DEMO-WL-0109', '深沟球轴承 6205-2RS', 'SKF 6205-2RS(个)', 1, 1, NULL, 1, 0, 150, 8.30, 1, '批次管理', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000110, 0, 1000, 'DEMO-WL-0110', '深沟球轴承 6208-2Z', 'SKF 6208-2Z(个)', 1, 1, NULL, 1, 0, 200, 15.50, 1, '批次管理', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000111, 0, 1000, 'DEMO-WL-0111', '圆锥滚子轴承 30206', 'HRB 30206(个)', 1, 1, NULL, 1, 0, 40, 21.60, 1, '批次管理', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000112, 0, 1000, 'DEMO-WL-0112', '深沟球轴承 6308-2Z', 'HRB 6308-2Z(个)', 1, 1, NULL, 0, 0, 40, 34.00, 1, '早期备库(呆滞)', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000201, 0, 1000, 'DEMO-WL-0201', '齿轮箱体部件', 'HT250 铸造+精加工(外协件)', 3, 1, NULL, 0, 0, 50, 380.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000202, 0, 1000, 'DEMO-WL-0202', '齿轮轴组', '20CrMnTi 渗碳淬火(外协件)', 3, 1, NULL, 0, 0, 30, 255.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000203, 0, 1000, 'DEMO-WL-0203', '传动轴毛坯', '45# 锻件 Φ35×420(外协件)', 3, 1, NULL, 0, 0, 60, 78.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000301, 0, 1000, 'DEMO-WL-0301', '精密齿轮箱 GB-40', '减速比1:40 额定扭矩400N·m', 2, 1, NULL, 0, 0, 15, 750.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000302, 0, 1000, 'DEMO-WL-0302', '精密齿轮箱 GB-63', '减速比1:63 额定扭矩630N·m', 2, 1, NULL, 0, 0, 20, 900.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000303, 0, 1000, 'DEMO-WL-0303', '精密齿轮箱 GB-90', '减速比1:90 额定扭矩900N·m', 2, 1, NULL, 0, 0, 10, 1250.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000304, 0, 1000, 'DEMO-WL-0304', '传动轴 CS-25', 'Φ25×560 45#调质', 2, 1, NULL, 0, 0, 40, 150.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000305, 0, 1000, 'DEMO-WL-0305', '传动轴 CS-32', 'Φ32×680 40Cr调质', 2, 1, NULL, 0, 0, 20, 215.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000306, 0, 1000, 'DEMO-WL-0306', '联轴器 LQ-1', '弹性柱销 NL1', 2, 1, NULL, 0, 0, 30, 280.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000307, 0, 1000, 'DEMO-WL-0307', '弹性联轴器 LQ-2', '梅花弹性 ML2', 2, 1, NULL, 0, 0, 20, 330.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000308, 0, 1000, 'DEMO-WL-0308', '膜片联轴器 LQ-3', 'JM3 不锈钢膜片', 2, 1, NULL, 0, 0, 10, 440.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000401, 0, 1000, 'DEMO-WL-0401', '木托盘', '1200×1000 免熏蒸(个)', 1, 1, NULL, 0, 0, 100, 95.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000402, 0, 1000, 'DEMO-WL-0402', '缠绕膜', '500mm×300m 17μm(卷)', 1, 1, NULL, 0, 0, 50, 12.00, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000403, 0, 1000, 'DEMO-WL-0403', '瓦楞纸箱', '600×400×400 三层(个)', 1, 1, NULL, 0, 0, 500, 6.50, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000404, 0, 1000, 'DEMO-WL-0404', '防锈袋', 'VCI 气相防锈 300×800(个)', 1, 1, NULL, 0, 0, 700, 1.20, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
  UNION ALL SELECT 520000405, 0, 1000, 'DEMO-WL-0405', '干燥剂', '硅橡胶 50g(个)', 1, 1, NULL, 0, 0, 1000, 0.30, 1, '', 520000505, '2026-03-28 09:00:00', 520000505, '2026-03-28 09:00:00', 0
) _v WHERE @g = 0;

-- ---- base_customer : 客户(8家) (8行) ----
SET @g := (SELECT COUNT(*) FROM `base_customer` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `base_customer` (`id`, `org_id`, `tenant_id`, `code`, `name`, `contact`, `phone`, `address`, `credit_code`, `settle_type_id`, `credit_limit`, `init_receivable`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `code`, `name`, `contact`, `phone`, `address`, `credit_code`, `settle_type_id`, `credit_limit`, `init_receivable`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520000201 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 'DEMO-KH-001' AS `code`, '苏州汇川技术有限公司' AS `name`, '李经理' AS `contact`, '0512-66670001' AS `phone`, '苏州市吴中区太湖东路1号' AS `address`, '91320506MA1ABC001X' AS `credit_code`, 4 AS `settle_type_id`, 1000000 AS `credit_limit`, 0 AS `init_receivable`, 1 AS `status`, '核心客户-自动化设备' AS `remark`, 520000503 AS `create_by`, '2026-03-05 09:00:00' AS `create_time`, 520000503 AS `update_by`, '2026-03-05 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520000202, 0, 1000, 'DEMO-KH-002', '南京埃斯顿自动化股份有限公司', '王工', '025-85550002', '南京市江宁区秣周东路9号', '91320115MA1BCD002X', 4, 800000, 0, 1, '机器人/伺服系统', 520000503, '2026-03-05 09:00:00', 520000503, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000203, 0, 1000, 'DEMO-KH-003', '常州铭赛数控机床有限公司', '赵总', '0519-88880003', '常州市武进国家高新区龙潜路9号', '91320412MA1CDE003X', 4, 600000, 0, 1, '数控机床整机', 520000503, '2026-03-05 09:00:00', 520000503, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000204, 0, 1000, 'DEMO-KH-004', '无锡先导智能装备股份有限公司', '钱工', '0510-81180004', '无锡市新吴区珠江路21号', '91320214MA1DEF004X', 2, 1000000, 0, 1, '锂电装备龙头', 520000503, '2026-03-05 09:00:00', 520000503, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000205, 0, 1000, 'DEMO-KH-005', '昆山柯尔克传动设备有限公司', '孙经理', '0512-57770005', '昆山市周市镇青阳北路301号', '91320583MA1EFG005X', 4, 300000, 0, 1, '传动件贸易商', 520000503, '2026-03-05 09:00:00', 520000503, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000206, 0, 1000, 'DEMO-KH-006', '上海奥特兰车辆配件有限公司', '周主管', '021-66660006', '上海市嘉定区安亭镇墨玉路88号', '91310114MA1FGH006X', 4, 500000, 0, 1, '汽车零部件配套', 520000503, '2026-03-05 09:00:00', 520000503, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000207, 0, 1000, 'DEMO-KH-007', '杭州东华链传动有限公司', '吴经理', '0571-86660007', '杭州市萧山区红垦农场垦辉六路5号', '91330109MA1GHI007X', 1, 200000, 0, 1, '链传动配套', 520000503, '2026-03-05 09:00:00', 520000503, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000208, 0, 1000, 'DEMO-KH-008', '宁波海天精工股份有限公司', '郑工', '0574-86660008', '宁波市北仑区小港街道江南东路9号', '91330206MA1HIJ008X', 2, 100000, 0, 1, '注塑机/机床', 520000503, '2026-03-05 09:00:00', 520000503, '2026-03-05 09:00:00', 0
) _v WHERE @g = 0;

-- ---- base_supplier : 供应商(6家) (6行) ----
SET @g := (SELECT COUNT(*) FROM `base_supplier` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `base_supplier` (`id`, `org_id`, `tenant_id`, `code`, `name`, `contact`, `phone`, `address`, `credit_code`, `settle_type_id`, `init_payable`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `code`, `name`, `contact`, `phone`, `address`, `credit_code`, `settle_type_id`, `init_payable`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520000301 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 'DEMO-GYS-001' AS `code`, '宝山钢贸(上海)有限公司' AS `name`, '王强' AS `contact`, '021-26000001' AS `phone`, '上海市宝山区铁力路785号' AS `address`, '91310113MA1IJK001Y' AS `credit_code`, 4 AS `settle_type_id`, 0 AS `init_payable`, 1 AS `status`, '板材框架协议供应商' AS `remark`, 520000502 AS `create_by`, '2026-03-05 09:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-03-05 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520000302, 0, 1000, 'DEMO-GYS-002', '苏州东南铝业有限公司', '陆敏', '0512-66000002', '苏州市相城区黄埭镇东桥长旺路86号', '91320507MA1JKL002Y', 4, 0, 1, '铝板供应商', 520000502, '2026-03-05 09:00:00', 520000502, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000303, 0, 1000, 'DEMO-GYS-003', '洛阳轴研科技股份有限公司', '杨工', '0379-64980003', '洛阳市涧西区建设路96号', '91410300MA1KLM003Y', 4, 0, 1, '轴承主力供应商(批次)', 520000502, '2026-03-05 09:00:00', 520000502, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000304, 0, 1000, 'DEMO-GYS-004', '宁波标准件制造有限公司', '何涛', '0574-86000004', '宁波市镇海区骆驼街道荣吉路558号', '91330211MA1LMN004Y', 4, 0, 1, '紧固件供应商', 520000502, '2026-03-05 09:00:00', 520000502, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000305, 0, 1000, 'DEMO-GYS-005', '昆山精工铜材有限公司', '管明', '0512-57000005', '昆山市玉山镇城北中路268号', '91320583MA1MNO005Y', 4, 0, 1, '铜材(批次)', 520000502, '2026-03-05 09:00:00', 520000502, '2026-03-05 09:00:00', 0
  UNION ALL SELECT 520000306, 0, 1000, 'DEMO-GYS-006', '苏州协精铸锻有限公司', '华总', '0512-68000006', '苏州市吴中区甪直镇淞南中路68号', '91320506MA1NOP006Y', 4, 0, 1, '铸锻件/外协加工', 520000502, '2026-03-05 09:00:00', 520000502, '2026-03-05 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_work_center : 工作中心 (3行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_work_center` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_work_center` (`id`, `org_id`, `tenant_id`, `code`, `name`, `workshop`, `capacity_hours_per_day`, `equipment_count`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `code`, `name`, `workshop`, `capacity_hours_per_day`, `equipment_count`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520080001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'WC202603010001' AS `code`, '金工车间' AS `name`, '车间一' AS `workshop`, 16 AS `capacity_hours_per_day`, 6 AS `equipment_count`, 'ENABLED' AS `status`, '车铣磨设备群' AS `remark`, 520000504 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520080002, 1, 1000, 'WC202603010002', '装配车间', '车间一', 8, 4, 'ENABLED', '齿轮箱装配线', 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520080003, 1, 1000, 'WC202603010003', '检测中心', '车间二', 8, 3, 'ENABLED', '三坐标/动平衡/跑合台', 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_operation : 工序 (7行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_operation` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_operation` (`id`, `org_id`, `tenant_id`, `code`, `name`, `default_work_center_id`, `setup_minutes`, `unit_minutes`, `outsource_flag`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `code`, `name`, `default_work_center_id`, `setup_minutes`, `unit_minutes`, `outsource_flag`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520085001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'OP202603010001' AS `code`, '车削' AS `name`, 520080001 AS `default_work_center_id`, 30 AS `setup_minutes`, 12 AS `unit_minutes`, 0 AS `outsource_flag`, 'ENABLED' AS `status`, NULL AS `remark`, 520000504 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520085002, 1, 1000, 'OP202603010002', '磨削', 520080001, 20, 8, 0, 'ENABLED', NULL, 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520085003, 1, 1000, 'OP202603010003', '校直动平衡', 520080003, 15, 6, 0, 'ENABLED', NULL, 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520085004, 1, 1000, 'OP202603010004', '部件预检', 520080003, 10, 3, 0, 'ENABLED', NULL, 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520085005, 1, 1000, 'OP202603010005', '部件装配', 520080002, 20, 25, 0, 'ENABLED', NULL, 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520085006, 1, 1000, 'OP202603010006', '空载测试', 520080003, 10, 15, 0, 'ENABLED', '装配后跑合测试', 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520085007, 1, 1000, 'OP202603010007', '喷漆包装', 520080002, 15, 5, 0, 'ENABLED', NULL, 520000504, '2026-03-01 09:00:00', 520000504, '2026-03-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_bom : BOM(齿轮箱三级+传动轴两级) (4行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_bom` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_bom` (`id`, `org_id`, `tenant_id`, `material_id`, `version`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `material_id`, `version`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520060001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520000301 AS `material_id`, 'V1.0' AS `version`, 'ACTIVE' AS `status`, 'GB-40 齿轮箱三级BOM(成品-部件-原材料)' AS `remark`, 520000504 AS `create_by`, '2026-03-10 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-03-12 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520060002, 1, 1000, 520000201, 'V1.0', 'ACTIVE', '箱体部件BOM(钢板耗用)', 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-12 09:00:00', 0
  UNION ALL SELECT 520060003, 1, 1000, 520000202, 'V1.0', 'ACTIVE', '齿轮轴组BOM(铜棒+轴承30206)', 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-12 09:00:00', 0
  UNION ALL SELECT 520060004, 1, 1000, 520000304, 'V1.0', 'ACTIVE', 'CS-25 传动轴两级BOM', 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-12 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_bom_item : BOM明细 (14行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_bom_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_bom_item` (`id`, `org_id`, `tenant_id`, `bom_id`, `material_id`, `qty`, `loss_rate`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `bom_id`, `material_id`, `qty`, `loss_rate`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520065001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520060001 AS `bom_id`, 520000201 AS `material_id`, 1.000000 AS `qty`, 2.00 AS `loss_rate`, NULL AS `remark`, 520000504 AS `create_by`, '2026-03-10 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-03-10 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520065002, 1, 1000, 520060001, 520000202, 1.000000, 1.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065003, 1, 1000, 520060001, 520000109, 2.000000, 0.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065004, 1, 1000, 520060001, 520000106, 12.000000, 0.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065005, 1, 1000, 520060001, 520000403, 1.000000, 0.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065006, 1, 1000, 520060001, 520000405, 2.000000, 0.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065010, 1, 1000, 520060002, 520000101, 18.000000, 5.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065011, 1, 1000, 520060002, 520000102, 6.000000, 5.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065020, 1, 1000, 520060003, 520000104, 1.200000, 3.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065021, 1, 1000, 520060003, 520000111, 2.000000, 1.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065030, 1, 1000, 520060004, 520000203, 1.000000, 2.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065031, 1, 1000, 520060004, 520000105, 4.000000, 0.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065032, 1, 1000, 520060004, 520000110, 2.000000, 1.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
  UNION ALL SELECT 520065033, 1, 1000, 520060004, 520000404, 1.000000, 0.00, NULL, 520000504, '2026-03-10 09:00:00', 520000504, '2026-03-10 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_routing : 工艺路线 (2行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_routing` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_routing` (`id`, `org_id`, `tenant_id`, `material_id`, `version`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `material_id`, `version`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520070001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520000301 AS `material_id`, 'V1.0' AS `version`, 'ACTIVE' AS `status`, 'GB-40 工艺路线(预检-装配-测试-包装)' AS `remark`, 520000504 AS `create_by`, '2026-03-11 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-03-12 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520070002, 1, 1000, 520000304, 'V1.0', 'ACTIVE', 'CS-25 工艺路线(车削-磨削-校直)', 520000504, '2026-03-11 09:00:00', 520000504, '2026-03-12 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_routing_step : 工艺路线工序 (7行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_routing_step` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_routing_step` (`id`, `org_id`, `tenant_id`, `routing_id`, `seq_no`, `operation_id`, `work_center_id`, `unit_minutes`, `report_flag`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `routing_id`, `seq_no`, `operation_id`, `work_center_id`, `unit_minutes`, `report_flag`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520075001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520070001 AS `routing_id`, 1 AS `seq_no`, 520085004 AS `operation_id`, 520080003 AS `work_center_id`, 3 AS `unit_minutes`, 1 AS `report_flag`, NULL AS `remark`, 520000504 AS `create_by`, '2026-03-11 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-03-11 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520075002, 1, 1000, 520070001, 2, 520085005, 520080002, 25, 1, NULL, 520000504, '2026-03-11 09:00:00', 520000504, '2026-03-11 09:00:00', 0
  UNION ALL SELECT 520075003, 1, 1000, 520070001, 3, 520085006, 520080003, 15, 1, NULL, 520000504, '2026-03-11 09:00:00', 520000504, '2026-03-11 09:00:00', 0
  UNION ALL SELECT 520075004, 1, 1000, 520070001, 4, 520085007, 520080002, 5, 1, NULL, 520000504, '2026-03-11 09:00:00', 520000504, '2026-03-11 09:00:00', 0
  UNION ALL SELECT 520075011, 1, 1000, 520070002, 1, 520085001, 520080001, 12, 1, NULL, 520000504, '2026-03-11 09:00:00', 520000504, '2026-03-11 09:00:00', 0
  UNION ALL SELECT 520075012, 1, 1000, 520070002, 2, 520085002, 520080001, 8, 1, NULL, 520000504, '2026-03-11 09:00:00', 520000504, '2026-03-11 09:00:00', 0
  UNION ALL SELECT 520075013, 1, 1000, 520070002, 3, 520085003, 520080003, 6, 1, NULL, 520000504, '2026-03-11 09:00:00', 520000504, '2026-03-11 09:00:00', 0
) _v WHERE @g = 0;

-- ---- qc_template : 检验模板 (2行) ----
SET @g := (SELECT COUNT(*) FROM `qc_template` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `qc_template` (`id`, `org_id`, `tenant_id`, `template_code`, `template_name`, `category_id`, `inspect_type`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `template_code`, `template_name`, `category_id`, `inspect_type`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520050001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'QT-DEMO-IQC' AS `template_code`, '来料检验通用模板(演示)' AS `template_name`, 1 AS `category_id`, 'IQC' AS `inspect_type`, 1 AS `status`, '原材料到货抽检' AS `remark`, 520000507 AS `create_by`, '2026-03-06 09:00:00' AS `create_time`, 520000507 AS `update_by`, '2026-03-06 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520050002, 1, 1000, 'QT-DEMO-FQC', '成品完工检验模板(演示)', 2, 'FQC', 1, '成品完工全检', 520000507, '2026-03-06 09:00:00', 520000507, '2026-03-06 09:00:00', 0
) _v WHERE @g = 0;

-- ---- qc_template_item : 检验模板明细 (6行) ----
SET @g := (SELECT COUNT(*) FROM `qc_template_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `qc_template_item` (`id`, `org_id`, `tenant_id`, `template_id`, `item_name`, `standard_value`, `lower_limit`, `upper_limit`, `inspect_method`, `sample_rate`, `seq`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `template_id`, `item_name`, `standard_value`, `lower_limit`, `upper_limit`, `inspect_method`, `sample_rate`, `seq`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520050101 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520050001 AS `template_id`, '外观' AS `item_name`, '无锈蚀/毛刺/划伤' AS `standard_value`, NULL AS `lower_limit`, NULL AS `upper_limit`, '目测' AS `inspect_method`, 100 AS `sample_rate`, 1 AS `seq`, NULL AS `remark`, 520000507 AS `create_by`, '2026-03-06 09:00:00' AS `create_time`, 520000507 AS `update_by`, '2026-03-06 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520050201, 1, 1000, 520050002, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, 1, NULL, 520000507, '2026-03-06 09:00:00', 520000507, '2026-03-06 09:00:00', 0
  UNION ALL SELECT 520050102, 1, 1000, 520050001, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺', 100, 2, NULL, 520000507, '2026-03-06 09:00:00', 520000507, '2026-03-06 09:00:00', 0
  UNION ALL SELECT 520050202, 1, 1000, 520050002, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺', 100, 2, NULL, 520000507, '2026-03-06 09:00:00', 520000507, '2026-03-06 09:00:00', 0
  UNION ALL SELECT 520050103, 1, 1000, 520050001, '硬度', 'HRC28-32', 28, 32, '洛氏硬度计', 100, 3, NULL, 520000507, '2026-03-06 09:00:00', 520000507, '2026-03-06 09:00:00', 0
  UNION ALL SELECT 520050203, 1, 1000, 520050002, '硬度', 'HRC28-32', 28, 32, '洛氏硬度计', 100, 3, NULL, 520000507, '2026-03-06 09:00:00', 520000507, '2026-03-06 09:00:00', 0
) _v WHERE @g = 0;

-- ---- hr_employee : 员工档案 (9行) ----
SET @g := (SELECT COUNT(*) FROM `hr_employee` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `hr_employee` (`id`, `org_id`, `tenant_id`, `emp_no`, `name`, `gender`, `phone`, `id_card`, `dept_id`, `position`, `hire_date`, `resign_date`, `status`, `base_salary`, `user_id`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `emp_no`, `name`, `gender`, `phone`, `id_card`, `dept_id`, `position`, `hire_date`, `resign_date`, `status`, `base_salary`, `user_id`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520270001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'EMP202603010001' AS `emp_no`, '张国民' AS `name`, 1 AS `gender`, '13915400001' AS `phone`, '32058119900170001' AS `id_card`, 520000401 AS `dept_id`, '总经理' AS `position`, '2018-03-01' AS `hire_date`, NULL AS `resign_date`, 'ON_JOB' AS `status`, 25000 AS `base_salary`, 520000501 AS `user_id`, '演示员工档案' AS `remark`, 520000505 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520270002, 1, 1000, 'EMP202603010002', '王大成', 1, '13915400002', '32058119900170002', 520000402, '采购经理', '2019-06-01', NULL, 'ON_JOB', 12000, 520000502, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520270003, 1, 1000, 'EMP202603010003', '刘晓松', 1, '13915400003', '32058119900170003', 520000403, '销售经理', '2019-09-01', NULL, 'ON_JOB', 12000, 520000503, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520270004, 1, 1000, 'EMP201805120001', '周建国', 1, '13915400004', '32058119900170004', 520000406, '车间主任', '2018-05-12', NULL, 'ON_JOB', 10000, 520000504, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520270005, 1, 1000, 'EMP202003020001', '马琳', 2, '13915400005', '32058119900170005', 3, '会计主管', '2020-03-02', NULL, 'ON_JOB', 9000, 520000505, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520270006, 1, 1000, 'EMP202104150001', '胡国富', 1, '13915400006', '32058119900170006', 520000408, '仓管组长', '2021-04-15', NULL, 'ON_JOB', 7000, 520000506, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520270007, 1, 1000, 'EMP202109060001', '齐敏', 2, '13915400007', '32058119900170007', 520000409, '质检主管', '2021-09-06', NULL, 'ON_JOB', 8000, 520000507, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520270008, 1, 1000, 'EMP202203010001', '陈立福', 1, '13915400008', '32058119900170008', 520000406, '数控技工', '2022-03-01', NULL, 'ON_JOB', 7500, NULL, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
  UNION ALL SELECT 520270009, 1, 1000, 'EMP202308210001', '吴海', 1, '13915400009', '32058119900170009', 520000407, '数控技工', '2023-08-21', NULL, 'ON_JOB', 6800, NULL, '演示员工档案', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- hr_attendance : 考勤(9月) (36行) ----
SET @g := (SELECT COUNT(*) FROM `hr_attendance` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `hr_attendance` (`id`, `org_id`, `tenant_id`, `employee_id`, `att_date`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `employee_id`, `att_date`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520173001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520270001 AS `employee_id`, '2026-09-01' AS `att_date`, 'NORMAL' AS `status`, '9月考勤(演示)' AS `remark`, 520000505 AS `create_by`, '2026-09-01 08:05:00' AS `create_time`, 520000505 AS `update_by`, '2026-09-01 08:05:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520173002, 1, 1000, 520270002, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173003, 1, 1000, 520270003, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173004, 1, 1000, 520270004, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173005, 1, 1000, 520270005, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173006, 1, 1000, 520270006, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173007, 1, 1000, 520270007, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173008, 1, 1000, 520270008, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173009, 1, 1000, 520270009, '2026-09-01', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-01 08:05:00', 520000505, '2026-09-01 08:05:00', 0
  UNION ALL SELECT 520173010, 1, 1000, 520270001, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173011, 1, 1000, 520270002, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173012, 1, 1000, 520270003, '2026-09-02', 'LATE', NULL, 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173013, 1, 1000, 520270004, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173014, 1, 1000, 520270005, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173015, 1, 1000, 520270006, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173016, 1, 1000, 520270007, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173017, 1, 1000, 520270008, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173018, 1, 1000, 520270009, '2026-09-02', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-02 08:05:00', 520000505, '2026-09-02 08:05:00', 0
  UNION ALL SELECT 520173019, 1, 1000, 520270001, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173020, 1, 1000, 520270002, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173021, 1, 1000, 520270003, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173022, 1, 1000, 520270004, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173023, 1, 1000, 520270005, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173024, 1, 1000, 520270006, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173025, 1, 1000, 520270007, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173026, 1, 1000, 520270008, '2026-09-03', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173027, 1, 1000, 520270009, '2026-09-03', 'LEAVE', NULL, 520000505, '2026-09-03 08:05:00', 520000505, '2026-09-03 08:05:00', 0
  UNION ALL SELECT 520173028, 1, 1000, 520270001, '2026-09-04', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173029, 1, 1000, 520270002, '2026-09-04', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173030, 1, 1000, 520270003, '2026-09-04', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173031, 1, 1000, 520270004, '2026-09-04', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173032, 1, 1000, 520270005, '2026-09-04', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173033, 1, 1000, 520270006, '2026-09-04', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173034, 1, 1000, 520270007, '2026-09-04', 'NORMAL', '9月考勤(演示)', 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173035, 1, 1000, 520270008, '2026-09-04', 'OVERTIME', NULL, 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
  UNION ALL SELECT 520173036, 1, 1000, 520270009, '2026-09-04', 'OVERTIME', NULL, 520000505, '2026-09-04 08:05:00', 520000505, '2026-09-04 08:05:00', 0
) _v WHERE @g = 0;

-- ---- hr_piece_price : 计件价目 (2行) ----
SET @g := (SELECT COUNT(*) FROM `hr_piece_price` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `hr_piece_price` (`id`, `org_id`, `tenant_id`, `material_id`, `process_name`, `unit_price`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `material_id`, `process_name`, `unit_price`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520174001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520000301 AS `material_id`, '装配' AS `process_name`, 8.00 AS `unit_price`, 'ENABLED' AS `status`, '齿轮箱装配计件' AS `remark`, 520000505 AS `create_by`, '2026-03-01 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520174002, 1, 1000, 520000304, '车削', 2.50, 'ENABLED', '传动轴车削计件', 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- hr_piece_wage : 计件工资期间 (5行) ----
SET @g := (SELECT COUNT(*) FROM `hr_piece_wage` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `hr_piece_wage` (`id`, `org_id`, `tenant_id`, `period`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `period`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520175001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, '2026-04' AS `period`, 'CLOSED' AS `status`, '演示计件工资期间' AS `remark`, 520000505 AS `create_by`, '2026-04-01 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-01 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520175002, 1, 1000, '2026-05', 'CLOSED', '演示计件工资期间', 520000505, '2026-05-01 09:00:00', 520000505, '2026-05-01 09:00:00', 0
  UNION ALL SELECT 520175003, 1, 1000, '2026-06', 'CLOSED', '演示计件工资期间', 520000505, '2026-06-01 09:00:00', 520000505, '2026-06-01 09:00:00', 0
  UNION ALL SELECT 520175004, 1, 1000, '2026-07', 'CLOSED', '演示计件工资期间', 520000505, '2026-07-01 09:00:00', 520000505, '2026-07-01 09:00:00', 0
  UNION ALL SELECT 520175005, 1, 1000, '2026-08', 'OPEN', '演示计件工资期间', 520000505, '2026-08-01 09:00:00', 520000505, '2026-08-01 09:00:00', 0
) _v WHERE @g = 0;

-- ---- hr_piece_wage_detail : 计件工资明细 (10行) ----
SET @g := (SELECT COUNT(*) FROM `hr_piece_wage_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `hr_piece_wage_detail` (`id`, `org_id`, `tenant_id`, `period_id`, `employee_id`, `work_order_id`, `work_order_no`, `material_id`, `process_name`, `qty`, `unit_price`, `amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `period_id`, `employee_id`, `work_order_id`, `work_order_no`, `material_id`, `process_name`, `qty`, `unit_price`, `amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520176001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520175001 AS `period_id`, 520270004 AS `employee_id`, 520133001 AS `work_order_id`, 'MO202604100001' AS `work_order_no`, 520000301 AS `material_id`, '装配' AS `process_name`, 10 AS `qty`, 8.00 AS `unit_price`, 80.00 AS `amount`, 520000505 AS `create_by`, '2026-04-28 18:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-28 18:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520176002, 1, 1000, 520175001, 520270004, 520133002, 'MO202604180001', 520000304, '车削', 30, 2.50, 75.00, 520000505, '2026-04-30 18:00:00', 520000505, '2026-04-30 18:00:00', 0
  UNION ALL SELECT 520176003, 1, 1000, 520175002, 520270004, 520133003, 'MO202605120001', 520000301, '装配', 12, 8.00, 96.00, 520000505, '2026-05-24 18:00:00', 520000505, '2026-05-24 18:00:00', 0
  UNION ALL SELECT 520176004, 1, 1000, 520175002, 520270004, 520133004, 'MO202605190001', 520000304, '车削', 40, 2.50, 100.00, 520000505, '2026-05-26 18:00:00', 520000505, '2026-05-26 18:00:00', 0
  UNION ALL SELECT 520176005, 1, 1000, 520175003, 520270004, 520133005, 'MO202606080001', 520000301, '装配', 12, 8.00, 96.00, 520000505, '2026-06-20 18:00:00', 520000505, '2026-06-20 18:00:00', 0
  UNION ALL SELECT 520176006, 1, 1000, 520175003, 520270004, 520133006, 'MO202606110001', 520000304, '车削', 45, 2.50, 112.50, 520000505, '2026-06-18 18:00:00', 520000505, '2026-06-18 18:00:00', 0
  UNION ALL SELECT 520176007, 1, 1000, 520175004, 520270004, 520133007, 'MO202607100001', 520000301, '装配', 6, 8.00, 48.00, 520000505, '2026-07-22 18:00:00', 520000505, '2026-07-22 18:00:00', 0
  UNION ALL SELECT 520176008, 1, 1000, 520175004, 520270004, 520133008, 'MO202607140001', 520000304, '车削', 20, 2.50, 50.00, 520000505, '2026-07-24 18:00:00', 520000505, '2026-07-24 18:00:00', 0
  UNION ALL SELECT 520176009, 1, 1000, 520175005, 520270004, 520133009, 'MO202608060001', 520000301, '装配', 14, 8.00, 112.00, 520000505, '2026-08-22 18:00:00', 520000505, '2026-08-22 18:00:00', 0
  UNION ALL SELECT 520176010, 1, 1000, 520175005, 520270004, 520133010, 'MO202608030001', 520000304, '车削', 55, 2.50, 137.50, 520000505, '2026-08-20 18:00:00', 520000505, '2026-08-20 18:00:00', 0
) _v WHERE @g = 0;

-- ---- inv_other_in : 期初其他入库 (2行) ----
SET @g := (SELECT COUNT(*) FROM `inv_other_in` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_other_in` (`id`, `org_id`, `tenant_id`, `no`, `warehouse_id`, `reason`, `status`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `no`, `warehouse_id`, `reason`, `status`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520141001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'OI202602270001' AS `no`, 1 AS `warehouse_id`, '2026年初备库' AS `reason`, 1 AS `status`, '2026-02-27 15:00:00' AS `audit_time`, '演示期初铺垫' AS `remark`, 520000506 AS `create_by`, '2026-02-26 14:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-02-27 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520141002, 1, 1000, 'OI202603280001', 1, '2026-03月末结转', 1, '2026-03-28 10:30:00', '演示期初铺垫', 520000506, '2026-03-27 09:30:00', 520000506, '2026-03-28 09:30:00', 0
) _v WHERE @g = 0;

-- ---- inv_other_in_line : 期初其他入库行 (28行) ----
SET @g := (SELECT COUNT(*) FROM `inv_other_in_line` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_other_in_line` (`id`, `org_id`, `tenant_id`, `other_in_id`, `material_id`, `batch_no`, `qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `other_in_id`, `material_id`, `batch_no`, `qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520142001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520141001 AS `other_in_id`, 520000112 AS `material_id`, '' AS `batch_no`, 120 AS `qty`, '期初备库' AS `remark`, 520000506 AS `create_by`, '2026-02-27 14:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-02-27 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520142002, 1, 1000, 520141002, 520000101, '', 1200, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142003, 1, 1000, 520141002, 520000102, '', 800, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142004, 1, 1000, 520141002, 520000103, '', 300, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142005, 1, 1000, 520141002, 520000104, '2603-T', 150, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142006, 1, 1000, 520141002, 520000105, '', 20000, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142007, 1, 1000, 520141002, 520000106, '', 30000, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142008, 1, 1000, 520141002, 520000107, '', 5000, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142009, 1, 1000, 520141002, 520000108, '', 3000, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142010, 1, 1000, 520141002, 520000109, '2603-Z', 240, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142011, 1, 1000, 520141002, 520000110, '2603-Z', 320, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142012, 1, 1000, 520141002, 520000111, '2603-Z', 100, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142013, 1, 1000, 520141002, 520000201, '', 30, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142014, 1, 1000, 520141002, 520000202, '', 30, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142015, 1, 1000, 520141002, 520000203, '', 50, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142016, 1, 1000, 520141002, 520000301, '', 20, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142017, 1, 1000, 520141002, 520000302, '', 30, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142018, 1, 1000, 520141002, 520000303, '', 10, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142019, 1, 1000, 520141002, 520000304, '', 40, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142020, 1, 1000, 520141002, 520000305, '', 36, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142021, 1, 1000, 520141002, 520000306, '', 40, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142022, 1, 1000, 520141002, 520000307, '', 25, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142023, 1, 1000, 520141002, 520000308, '', 12, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142024, 1, 1000, 520141002, 520000401, '', 150, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142025, 1, 1000, 520141002, 520000402, '', 80, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142026, 1, 1000, 520141002, 520000403, '', 800, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142027, 1, 1000, 520141002, 520000404, '', 1000, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520142028, 1, 1000, 520141002, 520000405, '', 2000, '期初备库', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
) _v WHERE @g = 0;

-- ---- inv_other_out : 其他出库 (5行) ----
SET @g := (SELECT COUNT(*) FROM `inv_other_out` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_other_out` (`id`, `org_id`, `tenant_id`, `no`, `warehouse_id`, `reason`, `status`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `no`, `warehouse_id`, `reason`, `status`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520143001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'OO202605280001' AS `no`, 1 AS `warehouse_id`, '车间维修领用' AS `reason`, 1 AS `status`, '2026-05-28 12:00:00' AS `audit_time`, '车间维修领用' AS `remark`, 520000506 AS `create_by`, '2026-05-27 11:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-05-28 11:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520143002, 1, 1000, 'OO202606250001', 1, '研发样品领用', 1, '2026-06-25 12:00:00', '研发样品领用', 520000506, '2026-06-24 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520143003, 1, 1000, 'OO202607280001', 1, '车间一般领用', 1, '2026-07-28 12:00:00', '车间一般领用', 520000506, '2026-07-27 11:00:00', 520000506, '2026-07-28 11:00:00', 0
  UNION ALL SELECT 520143004, 1, 1000, 'OO202608240001', 1, '质检报废(FQC判定)', 1, '2026-08-24 12:00:00', '质检报废(FQC判定)', 520000506, '2026-08-23 11:00:00', 520000506, '2026-08-24 11:00:00', 0
  UNION ALL SELECT 520143005, 1, 1000, 'OO202608250001', 1, '发货耗用', 1, '2026-08-25 12:00:00', '发货耗用', 520000506, '2026-08-24 11:00:00', 520000506, '2026-08-25 11:00:00', 0
) _v WHERE @g = 0;

-- ---- inv_other_out_line : 其他出库行 (8行) ----
SET @g := (SELECT COUNT(*) FROM `inv_other_out_line` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_other_out_line` (`id`, `org_id`, `tenant_id`, `other_out_id`, `material_id`, `batch_no`, `qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `other_out_id`, `material_id`, `batch_no`, `qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520144001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520143001 AS `other_out_id`, 520000101 AS `material_id`, '' AS `batch_no`, 200 AS `qty`, '车间维修领用' AS `remark`, 520000506 AS `create_by`, '2026-05-28 11:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-05-28 11:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520144002, 1, 1000, 520143002, 520000102, '', 50, '研发样品领用', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520144003, 1, 1000, 520143002, 520000103, '', 30, '研发样品领用', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520144004, 1, 1000, 520143002, 520000104, '2603-T', 20, '研发样品领用', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520144005, 1, 1000, 520143003, 520000107, '', 500, '车间一般领用', 520000506, '2026-07-28 11:00:00', 520000506, '2026-07-28 11:00:00', 0
  UNION ALL SELECT 520144006, 1, 1000, 520143004, 520000301, '', 2, '质检报废(FQC判定)', 520000506, '2026-08-24 11:00:00', 520000506, '2026-08-24 11:00:00', 0
  UNION ALL SELECT 520144007, 1, 1000, 520143005, 520000401, '', 8, '发货耗用', 520000506, '2026-08-25 11:00:00', 520000506, '2026-08-25 11:00:00', 0
  UNION ALL SELECT 520144008, 1, 1000, 520143005, 520000402, '', 5, '发货耗用', 520000506, '2026-08-25 11:00:00', 520000506, '2026-08-25 11:00:00', 0
) _v WHERE @g = 0;

-- ---- inv_check : 盘点单 (1行) ----
SET @g := (SELECT COUNT(*) FROM `inv_check` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_check` (`id`, `org_id`, `tenant_id`, `no`, `warehouse_id`, `status`, `complete_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `no`, `warehouse_id`, `status`, `complete_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520145001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'CK202606300001' AS `no`, 1 AS `warehouse_id`, 1 AS `status`, '2026-06-30 15:30:00' AS `complete_time`, '年中抽样盘点(演示)' AS `remark`, 520000506 AS `create_by`, '2026-06-30 09:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-06-30 15:30:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- inv_check_detail : 盘点明细 (3行) ----
SET @g := (SELECT COUNT(*) FROM `inv_check_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_check_detail` (`id`, `org_id`, `tenant_id`, `check_id`, `material_id`, `batch_no`, `book_qty`, `actual_qty`, `diff_qty`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `check_id`, `material_id`, `batch_no`, `book_qty`, `actual_qty`, `diff_qty`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520146001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520145001 AS `check_id`, 520000405 AS `material_id`, '' AS `batch_no`, 1932.0000 AS `book_qty`, 1927.0000 AS `actual_qty`, -5 AS `diff_qty`, 520000506 AS `create_by`, '2026-06-30 10:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-06-30 15:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520146002, 1, 1000, 520145001, 520000106, '', 49592.0000, 49392.0000, -200, 520000506, '2026-06-30 10:00:00', 520000506, '2026-06-30 15:00:00', 0
  UNION ALL SELECT 520146003, 1, 1000, 520145001, 520000101, '', 6000.0000, 6010.0000, 10, 520000506, '2026-06-30 10:00:00', 520000506, '2026-06-30 15:00:00', 0
) _v WHERE @g = 0;

-- ---- pur_order : 采购订单 (26行) ----
SET @g := (SELECT COUNT(*) FROM `pur_order` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `pur_order` (`id`, `org_id`, `tenant_id`, `order_no`, `supplier_id`, `warehouse_id`, `order_date`, `total_amount`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `order_no`, `supplier_id`, `warehouse_id`, `order_date`, `total_amount`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520100001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'PO202604060001' AS `order_no`, 520000301 AS `supplier_id`, 1 AS `warehouse_id`, '2026-04-06' AS `order_date`, 20175.00 AS `total_amount`, 30 AS `status`, 520000501 AS `audit_by`, '2026-04-06 11:00:00' AS `audit_time`, '框架协议分批交货' AS `remark`, 520000502 AS `create_by`, '2026-04-06 09:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-04-06 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520100002, 1, 1000, 'PO202604070001', 520000303, 1, '2026-04-07', 4760.00, 30, 520000501, '2026-04-07 11:00:00', '苏州精工演示采购', 520000502, '2026-04-07 09:00:00', 520000502, '2026-04-07 09:00:00', 0
  UNION ALL SELECT 520100003, 1, 1000, 'PO202604080001', 520000304, 1, '2026-04-08', 10100.00, 30, 520000501, '2026-04-08 11:00:00', '苏州精工演示采购', 520000502, '2026-04-08 09:00:00', 520000502, '2026-04-08 09:00:00', 0
  UNION ALL SELECT 520100004, 1, 1000, 'PO202604090001', 520000306, 1, '2026-04-09', 4680.00, 30, 520000501, '2026-04-09 11:00:00', '苏州精工演示采购', 520000502, '2026-04-09 09:00:00', 520000502, '2026-04-09 09:00:00', 0
  UNION ALL SELECT 520100005, 1, 1000, 'PO202605060001', 520000302, 1, '2026-05-06', 9100.00, 30, 520000501, '2026-05-06 11:00:00', '苏州精工演示采购', 520000502, '2026-05-06 09:00:00', 520000502, '2026-05-06 09:00:00', 0
  UNION ALL SELECT 520100006, 1, 1000, 'PO202605070001', 520000303, 1, '2026-05-07', 1080.00, 30, 520000501, '2026-05-07 11:00:00', '苏州精工演示采购', 520000502, '2026-05-07 09:00:00', 520000502, '2026-05-07 09:00:00', 0
  UNION ALL SELECT 520100007, 1, 1000, 'PO202605080001', 520000306, 1, '2026-05-08', 19050.00, 30, 520000501, '2026-05-08 11:00:00', '苏州精工演示采购', 520000502, '2026-05-08 09:00:00', 520000502, '2026-05-08 09:00:00', 0
  UNION ALL SELECT 520100008, 1, 1000, 'PO202605090001', 520000301, 1, '2026-05-09', 9100.00, 30, 520000501, '2026-05-09 11:00:00', '苏州精工演示采购', 520000502, '2026-05-09 09:00:00', 520000502, '2026-05-09 09:00:00', 0
  UNION ALL SELECT 520100009, 1, 1000, 'PO202606050001', 520000304, 1, '2026-06-05', 5140.00, 30, 520000501, '2026-06-05 11:00:00', '苏州精工演示采购', 520000502, '2026-06-05 09:00:00', 520000502, '2026-06-05 09:00:00', 0
  UNION ALL SELECT 520100010, 1, 1000, 'PO202606060001', 520000306, 1, '2026-06-06', 6240.00, 30, 520000501, '2026-06-06 11:00:00', '苏州精工演示采购', 520000502, '2026-06-06 09:00:00', 520000502, '2026-06-06 09:00:00', 0
  UNION ALL SELECT 520100011, 1, 1000, 'PO202606070001', 520000305, 1, '2026-06-07', 17100.00, 30, 520000501, '2026-06-07 11:00:00', '苏州精工演示采购', 520000502, '2026-06-07 09:00:00', 520000502, '2026-06-07 09:00:00', 0
  UNION ALL SELECT 520100012, 1, 1000, 'PO202606080001', 520000301, 1, '2026-06-08', 4350.00, 30, 520000501, '2026-06-08 11:00:00', '苏州精工演示采购', 520000502, '2026-06-08 09:00:00', 520000502, '2026-06-08 09:00:00', 0
  UNION ALL SELECT 520100013, 1, 1000, 'PO202607060001', 520000306, 1, '2026-07-06', 12700.00, 30, 520000501, '2026-07-06 11:00:00', '苏州精工演示采购', 520000502, '2026-07-06 09:00:00', 520000502, '2026-07-06 09:00:00', 0
  UNION ALL SELECT 520100014, 1, 1000, 'PO202607070001', 520000303, 1, '2026-07-07', 3570.00, 30, 520000501, '2026-07-07 11:00:00', '苏州精工演示采购', 520000502, '2026-07-07 09:00:00', 520000502, '2026-07-07 09:00:00', 0
  UNION ALL SELECT 520100015, 1, 1000, 'PO202607080001', 520000304, 1, '2026-07-08', 2500.00, 30, 520000501, '2026-07-08 11:00:00', '苏州精工演示采购', 520000502, '2026-07-08 09:00:00', 520000502, '2026-07-08 09:00:00', 0
  UNION ALL SELECT 520100016, 1, 1000, 'PO202607090001', 520000301, 1, '2026-07-09', 3480.00, 30, 520000501, '2026-07-09 11:00:00', '苏州精工演示采购', 520000502, '2026-07-09 09:00:00', 520000502, '2026-07-09 09:00:00', 0
  UNION ALL SELECT 520100017, 1, 1000, 'PO202608050001', 520000306, 1, '2026-08-05', 15400.00, 30, 520000501, '2026-08-05 11:00:00', '旺季备货分批交货', 520000502, '2026-08-05 09:00:00', 520000502, '2026-08-05 09:00:00', 0
  UNION ALL SELECT 520100018, 1, 1000, 'PO202608060001', 520000303, 1, '2026-08-06', 3320.00, 30, 520000501, '2026-08-06 11:00:00', '苏州精工演示采购', 520000502, '2026-08-06 09:00:00', 520000502, '2026-08-06 09:00:00', 0
  UNION ALL SELECT 520100019, 1, 1000, 'PO202608070001', 520000301, 1, '2026-08-07', 11375.00, 30, 520000501, '2026-08-07 11:00:00', '苏州精工演示采购', 520000502, '2026-08-07 09:00:00', 520000502, '2026-08-07 09:00:00', 0
  UNION ALL SELECT 520100020, 1, 1000, 'PO202608080001', 520000302, 1, '2026-08-08', 5460.00, 30, 520000501, '2026-08-08 11:00:00', '苏州精工演示采购', 520000502, '2026-08-08 09:00:00', 520000502, '2026-08-08 09:00:00', 0
  UNION ALL SELECT 520100021, 1, 1000, 'PO202609010001', 520000306, 1, '2026-09-01', 9525.00, 30, 520000501, '2026-09-01 11:00:00', '苏州精工演示采购', 520000502, '2026-09-01 09:00:00', 520000502, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520100022, 1, 1000, 'PO202609020001', 520000304, 1, '2026-09-02', 3400.00, 30, 520000501, '2026-09-02 11:00:00', '苏州精工演示采购', 520000502, '2026-09-02 09:00:00', 520000502, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520100023, 1, 1000, 'PO202609020002', 520000301, 1, '2026-09-02', 9100.00, 10, 520000501, '2026-09-02 11:00:00', '9月中旬到货(在途)', 520000502, '2026-09-02 09:00:00', 520000502, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520100024, 1, 1000, 'PO202609020003', 520000303, 1, '2026-09-02', 3100.00, 30, 520000501, '2026-09-02 11:00:00', '苏州精工演示采购', 520000502, '2026-09-02 09:00:00', 520000502, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520100025, 1, 1000, 'PO202609030001', 520000306, 1, '2026-09-03', 4680.00, 0, NULL, NULL, '待总经理审批(演示审批流)', 520000502, '2026-09-03 09:00:00', 520000502, '2026-09-03 09:00:00', 0
  UNION ALL SELECT 520100026, 1, 1000, 'PO202609040001', 520000306, 1, '2026-09-04', 5100.00, 0, NULL, NULL, '待总经理审批(演示审批流)', 520000502, '2026-09-04 09:00:00', 520000502, '2026-09-04 09:00:00', 0
) _v WHERE @g = 0;

-- ---- pur_order_detail : 采购订单明细 (35行) ----
SET @g := (SELECT COUNT(*) FROM `pur_order_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `pur_order_detail` (`id`, `org_id`, `tenant_id`, `order_id`, `material_id`, `qty`, `price`, `tax_rate`, `amount`, `delivery_date`, `received_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `order_id`, `material_id`, `qty`, `price`, `tax_rate`, `amount`, `delivery_date`, `received_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520105001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520100001 AS `order_id`, 520000101 AS `material_id`, 3000 AS `qty`, 4.55 AS `price`, 13.00 AS `tax_rate`, 13650.00 AS `amount`, '2026-04-16' AS `delivery_date`, 3000.0000 AS `received_qty`, NULL AS `remark`, 520000502 AS `create_by`, '2026-04-06 09:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-04-06 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520105002, 1, 1000, 520100001, 520000102, 1500, 4.35, 13.00, 6525.00, '2026-04-16', 1500.0000, NULL, 520000502, '2026-04-06 09:00:00', 520000502, '2026-04-06 09:00:00', 0
  UNION ALL SELECT 520105003, 1, 1000, 520100002, 520000109, 200, 8.30, 13.00, 1660.00, '2026-04-17', 200, NULL, 520000502, '2026-04-07 09:00:00', 520000502, '2026-04-07 09:00:00', 0
  UNION ALL SELECT 520105004, 1, 1000, 520100002, 520000110, 200, 15.50, 13.00, 3100.00, '2026-04-17', 200, NULL, 520000502, '2026-04-07 09:00:00', 520000502, '2026-04-07 09:00:00', 0
  UNION ALL SELECT 520105005, 1, 1000, 520100003, 520000106, 20000, 0.25, 13.00, 5000.00, '2026-04-18', 20000, NULL, 520000502, '2026-04-08 09:00:00', 520000502, '2026-04-08 09:00:00', 0
  UNION ALL SELECT 520105006, 1, 1000, 520100003, 520000105, 30000, 0.17, 13.00, 5100.00, '2026-04-18', 30000, NULL, 520000502, '2026-04-08 09:00:00', 520000502, '2026-04-08 09:00:00', 0
  UNION ALL SELECT 520105007, 1, 1000, 520100004, 520000203, 60, 78.00, 13.00, 4680.00, '2026-04-19', 60, NULL, 520000502, '2026-04-09 09:00:00', 520000502, '2026-04-09 09:00:00', 0
  UNION ALL SELECT 520105008, 1, 1000, 520100005, 520000103, 500, 18.20, 13.00, 9100.00, '2026-05-16', 500, NULL, 520000502, '2026-05-06 09:00:00', 520000502, '2026-05-06 09:00:00', 0
  UNION ALL SELECT 520105009, 1, 1000, 520100006, 520000111, 50, 21.60, 13.00, 1080.00, '2026-05-17', 50, NULL, 520000502, '2026-05-07 09:00:00', 520000502, '2026-05-07 09:00:00', 0
  UNION ALL SELECT 520105010, 1, 1000, 520100007, 520000201, 30, 380.00, 13.00, 11400.00, '2026-05-18', 30, NULL, 520000502, '2026-05-08 09:00:00', 520000502, '2026-05-08 09:00:00', 0
  UNION ALL SELECT 520105011, 1, 1000, 520100007, 520000202, 30, 255.00, 13.00, 7650.00, '2026-05-18', 30, NULL, 520000502, '2026-05-08 09:00:00', 520000502, '2026-05-08 09:00:00', 0
  UNION ALL SELECT 520105012, 1, 1000, 520100008, 520000101, 2000, 4.55, 13.00, 9100.00, '2026-05-19', 2000, NULL, 520000502, '2026-05-09 09:00:00', 520000502, '2026-05-09 09:00:00', 0
  UNION ALL SELECT 520105013, 1, 1000, 520100009, 520000108, 5000, 0.50, 13.00, 2500.00, '2026-06-15', 5000, NULL, 520000502, '2026-06-05 09:00:00', 520000502, '2026-06-05 09:00:00', 0
  UNION ALL SELECT 520105014, 1, 1000, 520100009, 520000107, 8000, 0.33, 13.00, 2640.00, '2026-06-15', 8000, NULL, 520000502, '2026-06-05 09:00:00', 520000502, '2026-06-05 09:00:00', 0
  UNION ALL SELECT 520105015, 1, 1000, 520100010, 520000203, 80, 78.00, 13.00, 6240.00, '2026-06-16', 80, NULL, 520000502, '2026-06-06 09:00:00', 520000502, '2026-06-06 09:00:00', 0
  UNION ALL SELECT 520105016, 1, 1000, 520100011, 520000104, 300, 57.00, 13.00, 17100.00, '2026-06-17', 300, NULL, 520000502, '2026-06-07 09:00:00', 520000502, '2026-06-07 09:00:00', 0
  UNION ALL SELECT 520105017, 1, 1000, 520100012, 520000102, 1000, 4.35, 13.00, 4350.00, '2026-06-18', 1000, NULL, 520000502, '2026-06-08 09:00:00', 520000502, '2026-06-08 09:00:00', 0
  UNION ALL SELECT 520105018, 1, 1000, 520100013, 520000201, 20, 380.00, 13.00, 7600.00, '2026-07-16', 20, NULL, 520000502, '2026-07-06 09:00:00', 520000502, '2026-07-06 09:00:00', 0
  UNION ALL SELECT 520105019, 1, 1000, 520100013, 520000202, 20, 255.00, 13.00, 5100.00, '2026-07-16', 20, NULL, 520000502, '2026-07-06 09:00:00', 520000502, '2026-07-06 09:00:00', 0
  UNION ALL SELECT 520105020, 1, 1000, 520100014, 520000109, 150, 8.30, 13.00, 1245.00, '2026-07-17', 150, NULL, 520000502, '2026-07-07 09:00:00', 520000502, '2026-07-07 09:00:00', 0
  UNION ALL SELECT 520105021, 1, 1000, 520100014, 520000110, 150, 15.50, 13.00, 2325.00, '2026-07-17', 150, NULL, 520000502, '2026-07-07 09:00:00', 520000502, '2026-07-07 09:00:00', 0
  UNION ALL SELECT 520105022, 1, 1000, 520100015, 520000106, 10000, 0.25, 13.00, 2500.00, '2026-07-18', 10000, NULL, 520000502, '2026-07-08 09:00:00', 520000502, '2026-07-08 09:00:00', 0
  UNION ALL SELECT 520105023, 1, 1000, 520100016, 520000102, 800, 4.35, 13.00, 3480.00, '2026-07-19', 800, NULL, 520000502, '2026-07-09 09:00:00', 520000502, '2026-07-09 09:00:00', 0
  UNION ALL SELECT 520105024, 1, 1000, 520100017, 520000203, 100, 78.00, 13.00, 7800.00, '2026-08-15', 100.0000, NULL, 520000502, '2026-08-05 09:00:00', 520000502, '2026-08-05 09:00:00', 0
  UNION ALL SELECT 520105025, 1, 1000, 520100017, 520000201, 20, 380.00, 13.00, 7600.00, '2026-08-15', 20.0000, NULL, 520000502, '2026-08-05 09:00:00', 520000502, '2026-08-05 09:00:00', 0
  UNION ALL SELECT 520105026, 1, 1000, 520100018, 520000109, 400, 8.30, 13.00, 3320.00, '2026-08-16', 400, NULL, 520000502, '2026-08-06 09:00:00', 520000502, '2026-08-06 09:00:00', 0
  UNION ALL SELECT 520105027, 1, 1000, 520100019, 520000101, 2500, 4.55, 13.00, 11375.00, '2026-08-17', 2500, NULL, 520000502, '2026-08-07 09:00:00', 520000502, '2026-08-07 09:00:00', 0
  UNION ALL SELECT 520105028, 1, 1000, 520100020, 520000103, 300, 18.20, 13.00, 5460.00, '2026-08-18', 300, NULL, 520000502, '2026-08-08 09:00:00', 520000502, '2026-08-08 09:00:00', 0
  UNION ALL SELECT 520105029, 1, 1000, 520100021, 520000201, 15, 380.00, 13.00, 5700.00, '2026-09-11', 15, NULL, 520000502, '2026-09-01 09:00:00', 520000502, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520105030, 1, 1000, 520100021, 520000202, 15, 255.00, 13.00, 3825.00, '2026-09-11', 15, NULL, 520000502, '2026-09-01 09:00:00', 520000502, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520105031, 1, 1000, 520100022, 520000105, 20000, 0.17, 13.00, 3400.00, '2026-09-12', 20000, NULL, 520000502, '2026-09-02 09:00:00', 520000502, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520105032, 1, 1000, 520100023, 520000101, 2000, 4.55, 13.00, 9100.00, '2026-09-12', 0, NULL, 520000502, '2026-09-02 09:00:00', 520000502, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520105033, 1, 1000, 520100024, 520000110, 200, 15.50, 13.00, 3100.00, '2026-09-12', 200, NULL, 520000502, '2026-09-02 09:00:00', 520000502, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520105034, 1, 1000, 520100025, 520000203, 60, 78.00, 13.00, 4680.00, '2026-09-13', 0, NULL, 520000502, '2026-09-03 09:00:00', 520000502, '2026-09-03 09:00:00', 0
  UNION ALL SELECT 520105035, 1, 1000, 520100026, 520000202, 20, 255.00, 13.00, 5100.00, '2026-09-14', 0, NULL, 520000502, '2026-09-04 09:00:00', 520000502, '2026-09-04 09:00:00', 0
) _v WHERE @g = 0;

-- ---- pur_instock : 采购入库单 (25行) ----
SET @g := (SELECT COUNT(*) FROM `pur_instock` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `pur_instock` (`id`, `org_id`, `tenant_id`, `instock_no`, `order_id`, `supplier_id`, `warehouse_id`, `instock_date`, `total_amount`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `instock_no`, `order_id`, `supplier_id`, `warehouse_id`, `instock_date`, `total_amount`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520110001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'RK202604130001' AS `instock_no`, 520100001 AS `order_id`, 520000301 AS `supplier_id`, 1 AS `warehouse_id`, '2026-04-13' AS `instock_date`, 12105.00 AS `total_amount`, 10 AS `status`, 520000501 AS `audit_by`, '2026-04-13 13:00:00' AS `audit_time`, '正常收货' AS `remark`, 520000506 AS `create_by`, '2026-04-12 10:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-04-13 13:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520110002, 1, 1000, 'RK202604240001', 520100001, 520000301, 1, '2026-04-24', 8070.00, 10, 520000501, '2026-04-24 13:00:00', '正常收货', 520000506, '2026-04-23 10:00:00', 520000506, '2026-04-24 13:00:00', 0
  UNION ALL SELECT 520110003, 1, 1000, 'RK202604150001', 520100002, 520000303, 1, '2026-04-15', 4760.00, 10, 520000501, '2026-04-15 13:00:00', 'IQC合格后上架', 520000506, '2026-04-14 10:00:00', 520000506, '2026-04-15 13:00:00', 0
  UNION ALL SELECT 520110004, 1, 1000, 'RK202604160001', 520100003, 520000304, 1, '2026-04-16', 10100.00, 10, 520000501, '2026-04-16 13:00:00', '正常收货', 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-16 13:00:00', 0
  UNION ALL SELECT 520110005, 1, 1000, 'RK202604170001', 520100004, 520000306, 1, '2026-04-17', 4680.00, 10, 520000501, '2026-04-17 13:00:00', '正常收货', 520000506, '2026-04-16 10:00:00', 520000506, '2026-04-17 13:00:00', 0
  UNION ALL SELECT 520110006, 1, 1000, 'RK202605130001', 520100005, 520000302, 1, '2026-05-13', 9100.00, 10, 520000501, '2026-05-13 13:00:00', '正常收货', 520000506, '2026-05-12 10:00:00', 520000506, '2026-05-13 13:00:00', 0
  UNION ALL SELECT 520110007, 1, 1000, 'RK202605140001', 520100006, 520000303, 1, '2026-05-14', 1080.00, 10, 520000501, '2026-05-14 13:00:00', 'IQC合格后上架', 520000506, '2026-05-13 10:00:00', 520000506, '2026-05-14 13:00:00', 0
  UNION ALL SELECT 520110008, 1, 1000, 'RK202605150001', 520100007, 520000306, 1, '2026-05-15', 19050.00, 10, 520000501, '2026-05-15 13:00:00', '正常收货', 520000506, '2026-05-14 10:00:00', 520000506, '2026-05-15 13:00:00', 0
  UNION ALL SELECT 520110009, 1, 1000, 'RK202605160001', 520100008, 520000301, 1, '2026-05-16', 9100.00, 10, 520000501, '2026-05-16 13:00:00', '正常收货', 520000506, '2026-05-15 10:00:00', 520000506, '2026-05-16 13:00:00', 0
  UNION ALL SELECT 520110010, 1, 1000, 'RK202606110001', 520100009, 520000304, 1, '2026-06-11', 5140.00, 10, 520000501, '2026-06-11 13:00:00', '正常收货', 520000506, '2026-06-10 10:00:00', 520000506, '2026-06-11 13:00:00', 0
  UNION ALL SELECT 520110011, 1, 1000, 'RK202606120001', 520100010, 520000306, 1, '2026-06-12', 6240.00, 10, 520000501, '2026-06-12 13:00:00', '正常收货', 520000506, '2026-06-11 10:00:00', 520000506, '2026-06-12 13:00:00', 0
  UNION ALL SELECT 520110012, 1, 1000, 'RK202606130001', 520100011, 520000305, 1, '2026-06-13', 17100.00, 10, 520000501, '2026-06-13 13:00:00', '正常收货', 520000506, '2026-06-12 10:00:00', 520000506, '2026-06-13 13:00:00', 0
  UNION ALL SELECT 520110013, 1, 1000, 'RK202606140001', 520100012, 520000301, 1, '2026-06-14', 4350.00, 10, 520000501, '2026-06-14 13:00:00', '正常收货', 520000506, '2026-06-13 10:00:00', 520000506, '2026-06-14 13:00:00', 0
  UNION ALL SELECT 520110014, 1, 1000, 'RK202607130001', 520100013, 520000306, 1, '2026-07-13', 12700.00, 10, 520000501, '2026-07-13 13:00:00', '正常收货', 520000506, '2026-07-12 10:00:00', 520000506, '2026-07-13 13:00:00', 0
  UNION ALL SELECT 520110015, 1, 1000, 'RK202607140001', 520100014, 520000303, 1, '2026-07-14', 3570.00, 10, 520000501, '2026-07-14 13:00:00', 'IQC合格后上架', 520000506, '2026-07-13 10:00:00', 520000506, '2026-07-14 13:00:00', 0
  UNION ALL SELECT 520110016, 1, 1000, 'RK202607150001', 520100015, 520000304, 1, '2026-07-15', 2500.00, 10, 520000501, '2026-07-15 13:00:00', '正常收货', 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-15 13:00:00', 0
  UNION ALL SELECT 520110017, 1, 1000, 'RK202607160001', 520100016, 520000301, 1, '2026-07-16', 3480.00, 10, 520000501, '2026-07-16 13:00:00', '正常收货', 520000506, '2026-07-15 10:00:00', 520000506, '2026-07-16 13:00:00', 0
  UNION ALL SELECT 520110018, 1, 1000, 'RK202608120001', 520100017, 520000306, 1, '2026-08-12', 10780.00, 10, 520000501, '2026-08-12 13:00:00', '正常收货', 520000506, '2026-08-11 10:00:00', 520000506, '2026-08-12 13:00:00', 0
  UNION ALL SELECT 520110019, 1, 1000, 'RK202608260001', 520100017, 520000306, 1, '2026-08-26', 4620.00, 10, 520000501, '2026-08-26 13:00:00', '正常收货', 520000506, '2026-08-25 10:00:00', 520000506, '2026-08-26 13:00:00', 0
  UNION ALL SELECT 520110020, 1, 1000, 'RK202608130001', 520100018, 520000303, 1, '2026-08-13', 3320.00, 10, 520000501, '2026-08-13 13:00:00', 'IQC合格后上架', 520000506, '2026-08-12 10:00:00', 520000506, '2026-08-13 13:00:00', 0
  UNION ALL SELECT 520110021, 1, 1000, 'RK202608140001', 520100019, 520000301, 1, '2026-08-14', 11375.00, 10, 520000501, '2026-08-14 13:00:00', '正常收货', 520000506, '2026-08-13 10:00:00', 520000506, '2026-08-14 13:00:00', 0
  UNION ALL SELECT 520110022, 1, 1000, 'RK202608150001', 520100020, 520000302, 1, '2026-08-15', 5460.00, 10, 520000501, '2026-08-15 13:00:00', '正常收货', 520000506, '2026-08-14 10:00:00', 520000506, '2026-08-15 13:00:00', 0
  UNION ALL SELECT 520110023, 1, 1000, 'RK202609030001', 520100021, 520000306, 1, '2026-09-03', 9525.00, 10, 520000501, '2026-09-03 13:00:00', '正常收货', 520000506, '2026-09-02 10:00:00', 520000506, '2026-09-03 13:00:00', 0
  UNION ALL SELECT 520110024, 1, 1000, 'RK202609040001', 520100022, 520000304, 1, '2026-09-04', 3400.00, 10, 520000501, '2026-09-04 13:00:00', '正常收货', 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-04 13:00:00', 0
  UNION ALL SELECT 520110025, 1, 1000, 'RK202609040002', 520100024, 520000303, 1, '2026-09-04', 3100.00, 10, 520000501, '2026-09-04 13:00:00', 'IQC合格后上架', 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-04 13:00:00', 0
) _v WHERE @g = 0;

-- ---- pur_instock_detail : 采购入库明细 (36行) ----
SET @g := (SELECT COUNT(*) FROM `pur_instock_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `pur_instock_detail` (`id`, `org_id`, `tenant_id`, `instock_id`, `order_detail_id`, `material_id`, `qty`, `price`, `tax_rate`, `amount`, `returned_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `instock_id`, `order_detail_id`, `material_id`, `qty`, `price`, `tax_rate`, `amount`, `returned_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520113001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520110001 AS `instock_id`, 520105001 AS `order_detail_id`, 520000101 AS `material_id`, 1800.0000 AS `qty`, 4.5500 AS `price`, 13.00 AS `tax_rate`, 8190.00 AS `amount`, 0.0000 AS `returned_qty`, NULL AS `remark`, 520000506 AS `create_by`, '2026-04-13 10:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-04-13 10:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520113002, 1, 1000, 520110001, 520105002, 520000102, 900.0000, 4.3500, 13.00, 3915.00, 0.0000, NULL, 520000506, '2026-04-13 10:00:00', 520000506, '2026-04-13 10:00:00', 0
  UNION ALL SELECT 520113003, 1, 1000, 520110002, 520105001, 520000101, 1200.0000, 4.5500, 13.00, 5460.00, 0.0000, NULL, 520000506, '2026-04-24 10:00:00', 520000506, '2026-04-24 10:00:00', 0
  UNION ALL SELECT 520113004, 1, 1000, 520110002, 520105002, 520000102, 600.0000, 4.3500, 13.00, 2610.00, 0.0000, NULL, 520000506, '2026-04-24 10:00:00', 520000506, '2026-04-24 10:00:00', 0
  UNION ALL SELECT 520113005, 1, 1000, 520110003, 520105003, 520000109, 200.0000, 8.3000, 13.00, 1660.00, 0.0000, NULL, 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520113006, 1, 1000, 520110003, 520105004, 520000110, 200.0000, 15.5000, 13.00, 3100.00, 0.0000, NULL, 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520113007, 1, 1000, 520110004, 520105005, 520000106, 20000.0000, 0.2500, 13.00, 5000.00, 0.0000, NULL, 520000506, '2026-04-16 10:00:00', 520000506, '2026-04-16 10:00:00', 0
  UNION ALL SELECT 520113008, 1, 1000, 520110004, 520105006, 520000105, 30000.0000, 0.1700, 13.00, 5100.00, 0.0000, NULL, 520000506, '2026-04-16 10:00:00', 520000506, '2026-04-16 10:00:00', 0
  UNION ALL SELECT 520113009, 1, 1000, 520110005, 520105007, 520000203, 60.0000, 78.0000, 13.00, 4680.00, 0.0000, NULL, 520000506, '2026-04-17 10:00:00', 520000506, '2026-04-17 10:00:00', 0
  UNION ALL SELECT 520113010, 1, 1000, 520110006, 520105008, 520000103, 500.0000, 18.2000, 13.00, 9100.00, 0.0000, NULL, 520000506, '2026-05-13 10:00:00', 520000506, '2026-05-13 10:00:00', 0
  UNION ALL SELECT 520113011, 1, 1000, 520110007, 520105009, 520000111, 50.0000, 21.6000, 13.00, 1080.00, 0.0000, NULL, 520000506, '2026-05-14 10:00:00', 520000506, '2026-05-14 10:00:00', 0
  UNION ALL SELECT 520113012, 1, 1000, 520110008, 520105010, 520000201, 30.0000, 380.0000, 13.00, 11400.00, 0.0000, NULL, 520000506, '2026-05-15 10:00:00', 520000506, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520113013, 1, 1000, 520110008, 520105011, 520000202, 30.0000, 255.0000, 13.00, 7650.00, 0.0000, NULL, 520000506, '2026-05-15 10:00:00', 520000506, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520113014, 1, 1000, 520110009, 520105012, 520000101, 2000.0000, 4.5500, 13.00, 9100.00, 0.0000, NULL, 520000506, '2026-05-16 10:00:00', 520000506, '2026-05-16 10:00:00', 0
  UNION ALL SELECT 520113015, 1, 1000, 520110010, 520105013, 520000108, 5000.0000, 0.5000, 13.00, 2500.00, 0.0000, NULL, 520000506, '2026-06-11 10:00:00', 520000506, '2026-06-11 10:00:00', 0
  UNION ALL SELECT 520113016, 1, 1000, 520110010, 520105014, 520000107, 8000.0000, 0.3300, 13.00, 2640.00, 0.0000, NULL, 520000506, '2026-06-11 10:00:00', 520000506, '2026-06-11 10:00:00', 0
  UNION ALL SELECT 520113017, 1, 1000, 520110011, 520105015, 520000203, 80.0000, 78.0000, 13.00, 6240.00, 0.0000, NULL, 520000506, '2026-06-12 10:00:00', 520000506, '2026-06-12 10:00:00', 0
  UNION ALL SELECT 520113018, 1, 1000, 520110012, 520105016, 520000104, 300.0000, 57.0000, 13.00, 17100.00, 0.0000, NULL, 520000506, '2026-06-13 10:00:00', 520000506, '2026-06-13 10:00:00', 0
  UNION ALL SELECT 520113019, 1, 1000, 520110013, 520105017, 520000102, 1000.0000, 4.3500, 13.00, 4350.00, 60.0000, NULL, 520000506, '2026-06-14 10:00:00', 520000506, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520113020, 1, 1000, 520110014, 520105018, 520000201, 20.0000, 380.0000, 13.00, 7600.00, 0.0000, NULL, 520000506, '2026-07-13 10:00:00', 520000506, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520113021, 1, 1000, 520110014, 520105019, 520000202, 20.0000, 255.0000, 13.00, 5100.00, 0.0000, NULL, 520000506, '2026-07-13 10:00:00', 520000506, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520113022, 1, 1000, 520110015, 520105020, 520000109, 150.0000, 8.3000, 13.00, 1245.00, 0.0000, NULL, 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520113023, 1, 1000, 520110015, 520105021, 520000110, 150.0000, 15.5000, 13.00, 2325.00, 0.0000, NULL, 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520113024, 1, 1000, 520110016, 520105022, 520000106, 10000.0000, 0.2500, 13.00, 2500.00, 0.0000, NULL, 520000506, '2026-07-15 10:00:00', 520000506, '2026-07-15 10:00:00', 0
  UNION ALL SELECT 520113025, 1, 1000, 520110017, 520105023, 520000102, 800.0000, 4.3500, 13.00, 3480.00, 0.0000, NULL, 520000506, '2026-07-16 10:00:00', 520000506, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520113026, 1, 1000, 520110018, 520105024, 520000203, 70.0000, 78.0000, 13.00, 5460.00, 0.0000, NULL, 520000506, '2026-08-12 10:00:00', 520000506, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520113027, 1, 1000, 520110018, 520105025, 520000201, 14.0000, 380.0000, 13.00, 5320.00, 0.0000, NULL, 520000506, '2026-08-12 10:00:00', 520000506, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520113028, 1, 1000, 520110019, 520105024, 520000203, 30.0000, 78.0000, 13.00, 2340.00, 0.0000, NULL, 520000506, '2026-08-26 10:00:00', 520000506, '2026-08-26 10:00:00', 0
  UNION ALL SELECT 520113029, 1, 1000, 520110019, 520105025, 520000201, 6.0000, 380.0000, 13.00, 2280.00, 0.0000, NULL, 520000506, '2026-08-26 10:00:00', 520000506, '2026-08-26 10:00:00', 0
  UNION ALL SELECT 520113030, 1, 1000, 520110020, 520105026, 520000109, 400.0000, 8.3000, 13.00, 3320.00, 0.0000, NULL, 520000506, '2026-08-13 10:00:00', 520000506, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520113031, 1, 1000, 520110021, 520105027, 520000101, 2500.0000, 4.5500, 13.00, 11375.00, 0.0000, NULL, 520000506, '2026-08-14 10:00:00', 520000506, '2026-08-14 10:00:00', 0
  UNION ALL SELECT 520113032, 1, 1000, 520110022, 520105028, 520000103, 300.0000, 18.2000, 13.00, 5460.00, 0.0000, NULL, 520000506, '2026-08-15 10:00:00', 520000506, '2026-08-15 10:00:00', 0
  UNION ALL SELECT 520113033, 1, 1000, 520110023, 520105029, 520000201, 15.0000, 380.0000, 13.00, 5700.00, 0.0000, NULL, 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-03 10:00:00', 0
  UNION ALL SELECT 520113034, 1, 1000, 520110023, 520105030, 520000202, 15.0000, 255.0000, 13.00, 3825.00, 0.0000, NULL, 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-03 10:00:00', 0
  UNION ALL SELECT 520113035, 1, 1000, 520110024, 520105031, 520000105, 20000.0000, 0.1700, 13.00, 3400.00, 0.0000, NULL, 520000506, '2026-09-04 10:00:00', 520000506, '2026-09-04 10:00:00', 0
  UNION ALL SELECT 520113036, 1, 1000, 520110025, 520105033, 520000110, 200.0000, 15.5000, 13.00, 3100.00, 0.0000, NULL, 520000506, '2026-09-04 10:00:00', 520000506, '2026-09-04 10:00:00', 0
  UNION ALL SELECT 520113037, 1, 1000, 520110026, 520105049, 520000101, 10.0000, 11.3000, 13.00, 113.00, 0.0000, NULL, 1, '2026-09-06 13:39:37', 1, '2026-09-06 13:39:37', 0
  UNION ALL SELECT 520113038, 1, 1000, 520110027, 520105051, 520000101, 10.0000, 11.3000, 13.00, 113.00, 0.0000, NULL, 1, '2026-09-06 13:40:38', 1, '2026-09-06 13:40:38', 0
  UNION ALL SELECT 520113039, 1, 1000, 520110028, 520105052, 520000101, 10.0000, 11.3000, 13.00, 113.00, 0.0000, NULL, 1, '2026-09-06 13:41:23', 1, '2026-09-06 13:41:23', 0
  UNION ALL SELECT 520113040, 1, 1000, 520110029, 520105053, 520000101, 10.0000, 11.3000, 13.00, 113.00, 0.0000, NULL, 1, '2026-09-06 13:42:24', 1, '2026-09-06 13:42:24', 0
  UNION ALL SELECT 520113041, 1, 1000, 520110030, 520105055, 520000101, 10.0000, 11.3000, 13.00, 113.00, 0.0000, NULL, 1, '2026-09-06 13:46:45', 1, '2026-09-06 13:46:45', 0
  UNION ALL SELECT 520113042, 1, 1000, 520110031, 520105056, 520000101, 10.0000, 11.3000, 13.00, 113.00, 0.0000, NULL, 1, '2026-09-06 13:47:13', 1, '2026-09-06 13:47:13', 0
  UNION ALL SELECT 520113043, 1, 1000, 520110032, 520105057, 520000101, 10.0000, 11.3000, 13.00, 113.00, 0.0000, NULL, 1, '2026-09-06 13:48:33', 1, '2026-09-06 13:48:33', 0
  UNION ALL SELECT 520113044, 1, 1000, 520110033, 520105058, 520000101, 5.0000, 11.3000, 13.00, 56.50, 0.0000, NULL, 1, '2026-09-06 13:50:05', 1, '2026-09-06 13:50:05', 0
) _v WHERE @g = 0;

-- ---- pur_return : 采购退货单 (1行) ----
SET @g := (SELECT COUNT(*) FROM `pur_return` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `pur_return` (`id`, `org_id`, `tenant_id`, `return_no`, `instock_id`, `supplier_id`, `warehouse_id`, `return_date`, `total_amount`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `return_no`, `instock_id`, `supplier_id`, `warehouse_id`, `return_date`, `total_amount`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520116001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'RT202606200001' AS `return_no`, 520110013 AS `instock_id`, 520000301 AS `supplier_id`, 1 AS `warehouse_id`, '2026-06-20' AS `return_date`, 261.00 AS `total_amount`, 10 AS `status`, 520000501 AS `audit_by`, '2026-06-20 15:00:00' AS `audit_time`, 'IQC不合格退货(联动RJ处置单)' AS `remark`, 520000506 AS `create_by`, '2026-06-20 13:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-06-20 15:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- pur_return_detail : 采购退货明细 (1行) ----
SET @g := (SELECT COUNT(*) FROM `pur_return_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `pur_return_detail` (`id`, `org_id`, `tenant_id`, `return_id`, `instock_detail_id`, `material_id`, `qty`, `price`, `tax_rate`, `amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `return_id`, `instock_detail_id`, `material_id`, `qty`, `price`, `tax_rate`, `amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520117001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520116001 AS `return_id`, 520113019 AS `instock_detail_id`, 520000102 AS `material_id`, 60 AS `qty`, 4.35 AS `price`, 13.00 AS `tax_rate`, 261.00 AS `amount`, NULL AS `remark`, 520000506 AS `create_by`, '2026-06-20 14:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-06-20 14:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- sal_order : 销售订单 (29行) ----
SET @g := (SELECT COUNT(*) FROM `sal_order` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sal_order` (`id`, `org_id`, `tenant_id`, `order_no`, `customer_id`, `warehouse_id`, `order_date`, `total_qty`, `total_amount`, `status`, `remark`, `audit_by`, `audit_time`, `close_by`, `close_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `order_no`, `customer_id`, `warehouse_id`, `order_date`, `total_qty`, `total_amount`, `status`, `remark`, `audit_by`, `audit_time`, `close_by`, `close_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520120001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'SO202604030001' AS `order_no`, 520000201 AS `customer_id`, 1 AS `warehouse_id`, '2026-04-03' AS `order_date`, 5 AS `total_qty`, 30 AS `total_amount`, 10 AS `status`, '苏州精工演示销售' AS `remark`, 520000501 AS `audit_by`, '2026-04-03 11:30:00' AS `audit_time`, NULL AS `close_by`, NULL AS `close_time`, 520000503 AS `create_by`, '2026-04-03 09:30:00' AS `create_time`, 520000503 AS `update_by`, '2026-04-03 09:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520120002, 1, 1000, 'SO202604030002', 520000202, 1, '2026-04-03', 20, 30, 10, '苏州精工演示销售', 520000501, '2026-04-03 11:30:00', NULL, NULL, 520000503, '2026-04-03 09:30:00', 520000503, '2026-04-03 09:30:00', 0
  UNION ALL SELECT 520120003, 1, 1000, 'SO202604040001', 520000203, 1, '2026-04-04', 8, 30, 10, '苏州精工演示销售', 520000501, '2026-04-04 11:30:00', NULL, NULL, 520000503, '2026-04-04 09:30:00', 520000503, '2026-04-04 09:30:00', 0
  UNION ALL SELECT 520120004, 1, 1000, 'SO202604050001', 520000204, 1, '2026-04-05', 3, 30, 10, '苏州精工演示销售', 520000501, '2026-04-05 11:30:00', NULL, NULL, 520000503, '2026-04-05 09:30:00', 520000503, '2026-04-05 09:30:00', 0
  UNION ALL SELECT 520120005, 1, 1000, 'SO202605040001', 520000201, 1, '2026-05-04', 8, 30, 10, '苏州精工演示销售', 520000501, '2026-05-04 11:30:00', NULL, NULL, 520000503, '2026-05-04 09:30:00', 520000503, '2026-05-04 09:30:00', 0
  UNION ALL SELECT 520120006, 1, 1000, 'SO202605040002', 520000205, 1, '2026-05-04', 30, 30, 10, '苏州精工演示销售', 520000501, '2026-05-04 11:30:00', NULL, NULL, 520000503, '2026-05-04 09:30:00', 520000503, '2026-05-04 09:30:00', 0
  UNION ALL SELECT 520120007, 1, 1000, 'SO202605050001', 520000208, 1, '2026-05-05', 12, 30, 10, '苏州精工演示销售', 520000501, '2026-05-05 11:30:00', NULL, NULL, 520000503, '2026-05-05 09:30:00', 520000503, '2026-05-05 09:30:00', 0
  UNION ALL SELECT 520120008, 1, 1000, 'SO202605060001', 520000206, 1, '2026-05-06', 6, 30, 10, '苏州精工演示销售', 520000501, '2026-05-06 11:30:00', NULL, NULL, 520000503, '2026-05-06 09:30:00', 520000503, '2026-05-06 09:30:00', 0
  UNION ALL SELECT 520120009, 1, 1000, 'SO202605070001', 520000202, 1, '2026-05-07', 2, 30, 10, '苏州精工演示销售', 520000501, '2026-05-07 11:30:00', NULL, NULL, 520000503, '2026-05-07 09:30:00', 520000503, '2026-05-07 09:30:00', 0
  UNION ALL SELECT 520120010, 1, 1000, 'SO202606030001', 520000201, 1, '2026-06-03', 10, 30, 10, '苏州精工演示销售', 520000501, '2026-06-03 11:30:00', NULL, NULL, 520000503, '2026-06-03 09:30:00', 520000503, '2026-06-03 09:30:00', 0
  UNION ALL SELECT 520120011, 1, 1000, 'SO202606030002', 520000204, 1, '2026-06-03', 36, 30, 10, '苏州精工演示销售', 520000501, '2026-06-03 11:30:00', NULL, NULL, 520000503, '2026-06-03 09:30:00', 520000503, '2026-06-03 09:30:00', 0
  UNION ALL SELECT 520120012, 1, 1000, 'SO202606040001', 520000203, 1, '2026-06-04', 10, 30, 10, '苏州精工演示销售', 520000501, '2026-06-04 11:30:00', NULL, NULL, 520000503, '2026-06-04 09:30:00', 520000503, '2026-06-04 09:30:00', 0
  UNION ALL SELECT 520120013, 1, 1000, 'SO202606050001', 520000202, 1, '2026-06-05', 5, 30, 10, '苏州精工演示销售', 520000501, '2026-06-05 11:30:00', NULL, NULL, 520000503, '2026-06-05 09:30:00', 520000503, '2026-06-05 09:30:00', 0
  UNION ALL SELECT 520120014, 1, 1000, 'SO202606060001', 520000205, 1, '2026-06-06', 3, 30, 10, '苏州精工演示销售', 520000501, '2026-06-06 11:30:00', NULL, NULL, 520000503, '2026-06-06 09:30:00', 520000503, '2026-06-06 09:30:00', 0
  UNION ALL SELECT 520120015, 1, 1000, 'SO202607030001', 520000201, 1, '2026-07-03', 4, 30, 10, '苏州精工演示销售', 520000501, '2026-07-03 11:30:00', NULL, NULL, 520000503, '2026-07-03 09:30:00', 520000503, '2026-07-03 09:30:00', 0
  UNION ALL SELECT 520120016, 1, 1000, 'SO202607040001', 520000204, 1, '2026-07-04', 15, 30, 10, '苏州精工演示销售', 520000501, '2026-07-04 11:30:00', NULL, NULL, 520000503, '2026-07-04 09:30:00', 520000503, '2026-07-04 09:30:00', 0
  UNION ALL SELECT 520120017, 1, 1000, 'SO202607050001', 520000206, 1, '2026-07-05', 3, 30, 10, '苏州精工演示销售', 520000501, '2026-07-05 11:30:00', NULL, NULL, 520000503, '2026-07-05 09:30:00', 520000503, '2026-07-05 09:30:00', 0
  UNION ALL SELECT 520120018, 1, 1000, 'SO202608030001', 520000201, 1, '2026-08-03', 14, 20, 10, '苏州精工演示销售', 520000501, '2026-08-03 11:30:00', NULL, NULL, 520000503, '2026-08-03 09:30:00', 520000503, '2026-08-03 09:30:00', 0
  UNION ALL SELECT 520120019, 1, 1000, 'SO202608030002', 520000208, 1, '2026-08-03', 8, 30, 10, '苏州精工演示销售', 520000501, '2026-08-03 11:30:00', NULL, NULL, 520000503, '2026-08-03 09:30:00', 520000503, '2026-08-03 09:30:00', 0
  UNION ALL SELECT 520120020, 1, 1000, 'SO202608040001', 520000204, 1, '2026-08-04', 40, 30, 10, '苏州精工演示销售', 520000501, '2026-08-04 11:30:00', NULL, NULL, 520000503, '2026-08-04 09:30:00', 520000503, '2026-08-04 09:30:00', 0
  UNION ALL SELECT 520120021, 1, 1000, 'SO202608040002', 520000206, 1, '2026-08-04', 18, 30, 10, '苏州精工演示销售', 520000501, '2026-08-04 11:30:00', NULL, NULL, 520000503, '2026-08-04 09:30:00', 520000503, '2026-08-04 09:30:00', 0
  UNION ALL SELECT 520120022, 1, 1000, 'SO202608050001', 520000203, 1, '2026-08-05', 12, 30, 10, '苏州精工演示销售', 520000501, '2026-08-05 11:30:00', NULL, NULL, 520000503, '2026-08-05 09:30:00', 520000503, '2026-08-05 09:30:00', 0
  UNION ALL SELECT 520120023, 1, 1000, 'SO202608060001', 520000205, 1, '2026-08-06', 5, 30, 10, '苏州精工演示销售', 520000501, '2026-08-06 11:30:00', NULL, NULL, 520000503, '2026-08-06 09:30:00', 520000503, '2026-08-06 09:30:00', 0
  UNION ALL SELECT 520120024, 1, 1000, 'SO202609010001', 520000201, 1, '2026-09-01', 10, 30, 10, '苏州精工演示销售', 520000501, '2026-09-01 11:30:00', NULL, NULL, 520000503, '2026-09-01 09:30:00', 520000503, '2026-09-01 09:30:00', 0
  UNION ALL SELECT 520120025, 1, 1000, 'SO202609010002', 520000202, 1, '2026-09-01', 6, 30, 10, '苏州精工演示销售', 520000501, '2026-09-01 11:30:00', NULL, NULL, 520000503, '2026-09-01 09:30:00', 520000503, '2026-09-01 09:30:00', 0
  UNION ALL SELECT 520120026, 1, 1000, 'SO202609010003', 520000204, 1, '2026-09-01', 32, 30, 10, '苏州精工演示销售', 520000501, '2026-09-01 11:30:00', NULL, NULL, 520000503, '2026-09-01 09:30:00', 520000503, '2026-09-01 09:30:00', 0
  UNION ALL SELECT 520120027, 1, 1000, 'SO202609020001', 520000208, 1, '2026-09-02', 3, 30, 10, '苏州精工演示销售', 520000501, '2026-09-02 11:30:00', NULL, NULL, 520000503, '2026-09-02 09:30:00', 520000503, '2026-09-02 09:30:00', 0
  UNION ALL SELECT 520120028, 1, 1000, 'SO202609020002', 520000206, 1, '2026-09-02', 8, 30, 10, '苏州精工演示销售', 520000501, '2026-09-02 11:30:00', NULL, NULL, 520000503, '2026-09-02 09:30:00', 520000503, '2026-09-02 09:30:00', 0
  UNION ALL SELECT 520120029, 1, 1000, 'SO202609030001', 520000203, 1, '2026-09-03', 15, 10, 10, '苏州精工演示销售', 520000501, '2026-09-03 11:30:00', NULL, NULL, 520000503, '2026-09-03 09:30:00', 520000503, '2026-09-03 09:30:00', 0
) _v WHERE @g = 0;

-- ---- sal_order_detail : 销售订单明细 (29行) ----
SET @g := (SELECT COUNT(*) FROM `sal_order_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sal_order_detail` (`id`, `org_id`, `tenant_id`, `order_id`, `material_id`, `qty`, `price`, `amount`, `out_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `order_id`, `material_id`, `qty`, `price`, `amount`, `out_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520123001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520120001 AS `order_id`, 520000301 AS `material_id`, 5 AS `qty`, 1180 AS `price`, 5900.00 AS `amount`, 5 AS `out_qty`, NULL AS `remark`, 520000503 AS `create_by`, '2026-04-03 09:30:00' AS `create_time`, 520000503 AS `update_by`, '2026-04-03 09:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520123002, 1, 1000, 520120002, 520000304, 20, 265, 5300.00, 20, NULL, 520000503, '2026-04-03 09:30:00', 520000503, '2026-04-03 09:30:00', 0
  UNION ALL SELECT 520123003, 1, 1000, 520120003, 520000306, 8, 495, 3960.00, 8, NULL, 520000503, '2026-04-04 09:30:00', 520000503, '2026-04-04 09:30:00', 0
  UNION ALL SELECT 520123004, 1, 1000, 520120004, 520000302, 3, 1580, 4740.00, 3, NULL, 520000503, '2026-04-05 09:30:00', 520000503, '2026-04-05 09:30:00', 0
  UNION ALL SELECT 520123005, 1, 1000, 520120005, 520000301, 8, 1180, 9440.00, 8, NULL, 520000503, '2026-05-04 09:30:00', 520000503, '2026-05-04 09:30:00', 0
  UNION ALL SELECT 520123006, 1, 1000, 520120006, 520000304, 30, 265, 7950.00, 30, NULL, 520000503, '2026-05-04 09:30:00', 520000503, '2026-05-04 09:30:00', 0
  UNION ALL SELECT 520123007, 1, 1000, 520120007, 520000305, 12, 380, 4560.00, 12, NULL, 520000503, '2026-05-05 09:30:00', 520000503, '2026-05-05 09:30:00', 0
  UNION ALL SELECT 520123008, 1, 1000, 520120008, 520000307, 6, 590, 3540.00, 6, NULL, 520000503, '2026-05-06 09:30:00', 520000503, '2026-05-06 09:30:00', 0
  UNION ALL SELECT 520123009, 1, 1000, 520120009, 520000303, 2, 2180, 4360.00, 2, NULL, 520000503, '2026-05-07 09:30:00', 520000503, '2026-05-07 09:30:00', 0
  UNION ALL SELECT 520123010, 1, 1000, 520120010, 520000301, 10, 1180, 11800.00, 10, NULL, 520000503, '2026-06-03 09:30:00', 520000503, '2026-06-03 09:30:00', 0
  UNION ALL SELECT 520123011, 1, 1000, 520120011, 520000304, 36, 265, 9540.00, 36, NULL, 520000503, '2026-06-03 09:30:00', 520000503, '2026-06-03 09:30:00', 0
  UNION ALL SELECT 520123012, 1, 1000, 520120012, 520000306, 10, 495, 4950.00, 10, NULL, 520000503, '2026-06-04 09:30:00', 520000503, '2026-06-04 09:30:00', 0
  UNION ALL SELECT 520123013, 1, 1000, 520120013, 520000302, 5, 1580, 7900.00, 5, NULL, 520000503, '2026-06-05 09:30:00', 520000503, '2026-06-05 09:30:00', 0
  UNION ALL SELECT 520123014, 1, 1000, 520120014, 520000308, 3, 780, 2340.00, 3, NULL, 520000503, '2026-06-06 09:30:00', 520000503, '2026-06-06 09:30:00', 0
  UNION ALL SELECT 520123015, 1, 1000, 520120015, 520000301, 4, 1180, 4720.00, 4, NULL, 520000503, '2026-07-03 09:30:00', 520000503, '2026-07-03 09:30:00', 0
  UNION ALL SELECT 520123016, 1, 1000, 520120016, 520000304, 15, 265, 3975.00, 15, NULL, 520000503, '2026-07-04 09:30:00', 520000503, '2026-07-04 09:30:00', 0
  UNION ALL SELECT 520123017, 1, 1000, 520120017, 520000307, 3, 590, 1770.00, 3, NULL, 520000503, '2026-07-05 09:30:00', 520000503, '2026-07-05 09:30:00', 0
  UNION ALL SELECT 520123018, 1, 1000, 520120018, 520000301, 14, 1180, 16520.00, 12, NULL, 520000503, '2026-08-03 09:30:00', 520000503, '2026-08-03 09:30:00', 0
  UNION ALL SELECT 520123019, 1, 1000, 520120019, 520000302, 8, 1580, 12640.00, 8, NULL, 520000503, '2026-08-03 09:30:00', 520000503, '2026-08-03 09:30:00', 0
  UNION ALL SELECT 520123020, 1, 1000, 520120020, 520000304, 40, 265, 10600.00, 40, NULL, 520000503, '2026-08-04 09:30:00', 520000503, '2026-08-04 09:30:00', 0
  UNION ALL SELECT 520123021, 1, 1000, 520120021, 520000305, 18, 380, 6840.00, 18, NULL, 520000503, '2026-08-04 09:30:00', 520000503, '2026-08-04 09:30:00', 0
  UNION ALL SELECT 520123022, 1, 1000, 520120022, 520000306, 12, 495, 5940.00, 12, NULL, 520000503, '2026-08-05 09:30:00', 520000503, '2026-08-05 09:30:00', 0
  UNION ALL SELECT 520123023, 1, 1000, 520120023, 520000308, 5, 780, 3900.00, 5, NULL, 520000503, '2026-08-06 09:30:00', 520000503, '2026-08-06 09:30:00', 0
  UNION ALL SELECT 520123024, 1, 1000, 520120024, 520000301, 10, 1180, 11800.00, 10, NULL, 520000503, '2026-09-01 09:30:00', 520000503, '2026-09-01 09:30:00', 0
  UNION ALL SELECT 520123025, 1, 1000, 520120025, 520000302, 6, 1580, 9480.00, 6, NULL, 520000503, '2026-09-01 09:30:00', 520000503, '2026-09-01 09:30:00', 0
  UNION ALL SELECT 520123026, 1, 1000, 520120026, 520000304, 32, 265, 8480.00, 32, NULL, 520000503, '2026-09-01 09:30:00', 520000503, '2026-09-01 09:30:00', 0
  UNION ALL SELECT 520123027, 1, 1000, 520120027, 520000303, 3, 2180, 6540.00, 3, NULL, 520000503, '2026-09-02 09:30:00', 520000503, '2026-09-02 09:30:00', 0
  UNION ALL SELECT 520123028, 1, 1000, 520120028, 520000307, 8, 590, 4720.00, 8, NULL, 520000503, '2026-09-02 09:30:00', 520000503, '2026-09-02 09:30:00', 0
  UNION ALL SELECT 520123029, 1, 1000, 520120029, 520000306, 15, 495, 7425.00, 0, NULL, 520000503, '2026-09-03 09:30:00', 520000503, '2026-09-03 09:30:00', 0
) _v WHERE @g = 0;

-- ---- sal_outstock : 销售出库单 (28行) ----
SET @g := (SELECT COUNT(*) FROM `sal_outstock` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sal_outstock` (`id`, `org_id`, `tenant_id`, `out_no`, `order_id`, `order_no`, `customer_id`, `warehouse_id`, `out_date`, `total_qty`, `total_amount`, `status`, `remark`, `audit_by`, `audit_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `out_no`, `order_id`, `order_no`, `customer_id`, `warehouse_id`, `out_date`, `total_qty`, `total_amount`, `status`, `remark`, `audit_by`, `audit_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520126001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'XS202604100001' AS `out_no`, 520120001 AS `order_id`, 'SO202604030001' AS `order_no`, 520000201 AS `customer_id`, 1 AS `warehouse_id`, '2026-04-10' AS `out_date`, 5 AS `total_qty`, 5900.00 AS `total_amount`, 10 AS `status`, '按订单发货' AS `remark`, 520000501 AS `audit_by`, '2026-04-10 16:00:00' AS `audit_time`, 520000506 AS `create_by`, '2026-04-09 14:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-04-10 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520126002, 1, 1000, 'XS202604120001', 520120002, 'SO202604030002', 520000202, 1, '2026-04-12', 20, 5300.00, 10, '按订单发货', 520000501, '2026-04-12 16:00:00', 520000506, '2026-04-11 14:00:00', 520000506, '2026-04-12 14:00:00', 0
  UNION ALL SELECT 520126003, 1, 1000, 'XS202604150001', 520120003, 'SO202604040001', 520000203, 1, '2026-04-15', 8, 3960.00, 10, '按订单发货', 520000501, '2026-04-15 16:00:00', 520000506, '2026-04-14 14:00:00', 520000506, '2026-04-15 14:00:00', 0
  UNION ALL SELECT 520126004, 1, 1000, 'XS202604180001', 520120004, 'SO202604050001', 520000204, 1, '2026-04-18', 3, 4740.00, 10, '按订单发货', 520000501, '2026-04-18 16:00:00', 520000506, '2026-04-17 14:00:00', 520000506, '2026-04-18 14:00:00', 0
  UNION ALL SELECT 520126005, 1, 1000, 'XS202605100001', 520120005, 'SO202605040001', 520000201, 1, '2026-05-10', 8, 9440.00, 10, '按订单发货', 520000501, '2026-05-10 16:00:00', 520000506, '2026-05-09 14:00:00', 520000506, '2026-05-10 14:00:00', 0
  UNION ALL SELECT 520126006, 1, 1000, 'XS202605120001', 520120006, 'SO202605040002', 520000205, 1, '2026-05-12', 30, 7950.00, 10, '按订单发货', 520000501, '2026-05-12 16:00:00', 520000506, '2026-05-11 14:00:00', 520000506, '2026-05-12 14:00:00', 0
  UNION ALL SELECT 520126007, 1, 1000, 'XS202605140001', 520120007, 'SO202605050001', 520000208, 1, '2026-05-14', 12, 4560.00, 10, '按订单发货', 520000501, '2026-05-14 16:00:00', 520000506, '2026-05-13 14:00:00', 520000506, '2026-05-14 14:00:00', 0
  UNION ALL SELECT 520126008, 1, 1000, 'XS202605160001', 520120008, 'SO202605060001', 520000206, 1, '2026-05-16', 6, 3540.00, 10, '按订单发货', 520000501, '2026-05-16 16:00:00', 520000506, '2026-05-15 14:00:00', 520000506, '2026-05-16 14:00:00', 0
  UNION ALL SELECT 520126009, 1, 1000, 'XS202605200001', 520120009, 'SO202605070001', 520000202, 1, '2026-05-20', 2, 4360.00, 10, '按订单发货', 520000501, '2026-05-20 16:00:00', 520000506, '2026-05-19 14:00:00', 520000506, '2026-05-20 14:00:00', 0
  UNION ALL SELECT 520126010, 1, 1000, 'XS202606100001', 520120010, 'SO202606030001', 520000201, 1, '2026-06-10', 10, 11800.00, 10, '按订单发货', 520000501, '2026-06-10 16:00:00', 520000506, '2026-06-09 14:00:00', 520000506, '2026-06-10 14:00:00', 0
  UNION ALL SELECT 520126011, 1, 1000, 'XS202606120001', 520120011, 'SO202606030002', 520000204, 1, '2026-06-12', 36, 9540.00, 10, '按订单发货', 520000501, '2026-06-12 16:00:00', 520000506, '2026-06-11 14:00:00', 520000506, '2026-06-12 14:00:00', 0
  UNION ALL SELECT 520126012, 1, 1000, 'XS202606150001', 520120012, 'SO202606040001', 520000203, 1, '2026-06-15', 10, 4950.00, 10, '按订单发货', 520000501, '2026-06-15 16:00:00', 520000506, '2026-06-14 14:00:00', 520000506, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520126013, 1, 1000, 'XS202606180001', 520120013, 'SO202606050001', 520000202, 1, '2026-06-18', 5, 7900.00, 10, '按订单发货', 520000501, '2026-06-18 16:00:00', 520000506, '2026-06-17 14:00:00', 520000506, '2026-06-18 14:00:00', 0
  UNION ALL SELECT 520126014, 1, 1000, 'XS202606200001', 520120014, 'SO202606060001', 520000205, 1, '2026-06-20', 3, 2340.00, 10, '按订单发货', 520000501, '2026-06-20 16:00:00', 520000506, '2026-06-19 14:00:00', 520000506, '2026-06-20 14:00:00', 0
  UNION ALL SELECT 520126015, 1, 1000, 'XS202607100001', 520120015, 'SO202607030001', 520000201, 1, '2026-07-10', 4, 4720.00, 10, '按订单发货', 520000501, '2026-07-10 16:00:00', 520000506, '2026-07-09 14:00:00', 520000506, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520126016, 1, 1000, 'XS202607130001', 520120016, 'SO202607040001', 520000204, 1, '2026-07-13', 15, 3975.00, 10, '按订单发货', 520000501, '2026-07-13 16:00:00', 520000506, '2026-07-12 14:00:00', 520000506, '2026-07-13 14:00:00', 0
  UNION ALL SELECT 520126017, 1, 1000, 'XS202607150001', 520120017, 'SO202607050001', 520000206, 1, '2026-07-15', 3, 1770.00, 10, '按订单发货', 520000501, '2026-07-15 16:00:00', 520000506, '2026-07-14 14:00:00', 520000506, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520126018, 1, 1000, 'XS202608100001', 520120018, 'SO202608030001', 520000201, 1, '2026-08-10', 12, 14160.00, 10, '按订单发货', 520000501, '2026-08-10 16:00:00', 520000506, '2026-08-09 14:00:00', 520000506, '2026-08-10 14:00:00', 0
  UNION ALL SELECT 520126019, 1, 1000, 'XS202608120001', 520120019, 'SO202608030002', 520000208, 1, '2026-08-12', 8, 12640.00, 10, '按订单发货', 520000501, '2026-08-12 16:00:00', 520000506, '2026-08-11 14:00:00', 520000506, '2026-08-12 14:00:00', 0
  UNION ALL SELECT 520126020, 1, 1000, 'XS202608140001', 520120020, 'SO202608040001', 520000204, 1, '2026-08-14', 40, 10600.00, 10, '按订单发货', 520000501, '2026-08-14 16:00:00', 520000506, '2026-08-13 14:00:00', 520000506, '2026-08-14 14:00:00', 0
  UNION ALL SELECT 520126021, 1, 1000, 'XS202608150001', 520120021, 'SO202608040002', 520000206, 1, '2026-08-15', 18, 6840.00, 10, '按订单发货', 520000501, '2026-08-15 16:00:00', 520000506, '2026-08-14 14:00:00', 520000506, '2026-08-15 14:00:00', 0
  UNION ALL SELECT 520126022, 1, 1000, 'XS202608180001', 520120022, 'SO202608050001', 520000203, 1, '2026-08-18', 12, 5940.00, 10, '按订单发货', 520000501, '2026-08-18 16:00:00', 520000506, '2026-08-17 14:00:00', 520000506, '2026-08-18 14:00:00', 0
  UNION ALL SELECT 520126023, 1, 1000, 'XS202608200001', 520120023, 'SO202608060001', 520000205, 1, '2026-08-20', 5, 3900.00, 10, '按订单发货', 520000501, '2026-08-20 16:00:00', 520000506, '2026-08-19 14:00:00', 520000506, '2026-08-20 14:00:00', 0
  UNION ALL SELECT 520126024, 1, 1000, 'XS202609020001', 520120024, 'SO202609010001', 520000201, 1, '2026-09-02', 10, 11800.00, 10, '按订单发货', 520000501, '2026-09-02 16:00:00', 520000506, '2026-09-01 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520126025, 1, 1000, 'XS202609020002', 520120025, 'SO202609010002', 520000202, 1, '2026-09-02', 6, 9480.00, 10, '按订单发货', 520000501, '2026-09-02 16:00:00', 520000506, '2026-09-01 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520126026, 1, 1000, 'XS202609030001', 520120026, 'SO202609010003', 520000204, 1, '2026-09-03', 32, 8480.00, 10, '按订单发货', 520000501, '2026-09-03 16:00:00', 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520126027, 1, 1000, 'XS202609030002', 520120027, 'SO202609020001', 520000208, 1, '2026-09-03', 3, 6540.00, 10, '按订单发货', 520000501, '2026-09-03 16:00:00', 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520126028, 1, 1000, 'XS202609040001', 520120028, 'SO202609020002', 520000206, 1, '2026-09-04', 8, 4720.00, 10, '按订单发货', 520000501, '2026-09-04 16:00:00', 520000506, '2026-09-03 14:00:00', 520000506, '2026-09-04 14:00:00', 0
) _v WHERE @g = 0;

-- ---- sal_outstock_detail : 销售出库明细 (28行) ----
SET @g := (SELECT COUNT(*) FROM `sal_outstock_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sal_outstock_detail` (`id`, `org_id`, `tenant_id`, `out_id`, `order_detail_id`, `material_id`, `qty`, `price`, `amount`, `returned_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `out_id`, `order_detail_id`, `material_id`, `qty`, `price`, `amount`, `returned_qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520128001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520126001 AS `out_id`, 520123001 AS `order_detail_id`, 520000301 AS `material_id`, 5.0000 AS `qty`, 1180.00 AS `price`, 5900.00 AS `amount`, 0.0000 AS `returned_qty`, NULL AS `remark`, 520000506 AS `create_by`, '2026-04-10 14:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-04-10 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520128002, 1, 1000, 520126002, 520123002, 520000304, 20.0000, 265.00, 5300.00, 0.0000, NULL, 520000506, '2026-04-12 14:00:00', 520000506, '2026-04-12 14:00:00', 0
  UNION ALL SELECT 520128003, 1, 1000, 520126003, 520123003, 520000306, 8.0000, 495.00, 3960.00, 0.0000, NULL, 520000506, '2026-04-15 14:00:00', 520000506, '2026-04-15 14:00:00', 0
  UNION ALL SELECT 520128004, 1, 1000, 520126004, 520123004, 520000302, 3.0000, 1580.00, 4740.00, 0.0000, NULL, 520000506, '2026-04-18 14:00:00', 520000506, '2026-04-18 14:00:00', 0
  UNION ALL SELECT 520128005, 1, 1000, 520126005, 520123005, 520000301, 8.0000, 1180.00, 9440.00, 0.0000, NULL, 520000506, '2026-05-10 14:00:00', 520000506, '2026-05-10 14:00:00', 0
  UNION ALL SELECT 520128006, 1, 1000, 520126006, 520123006, 520000304, 30.0000, 265.00, 7950.00, 2.0000, NULL, 520000506, '2026-05-12 14:00:00', 520000506, '2026-05-12 14:00:00', 0
  UNION ALL SELECT 520128007, 1, 1000, 520126007, 520123007, 520000305, 12.0000, 380.00, 4560.00, 0.0000, NULL, 520000506, '2026-05-14 14:00:00', 520000506, '2026-05-14 14:00:00', 0
  UNION ALL SELECT 520128008, 1, 1000, 520126008, 520123008, 520000307, 6.0000, 590.00, 3540.00, 0.0000, NULL, 520000506, '2026-05-16 14:00:00', 520000506, '2026-05-16 14:00:00', 0
  UNION ALL SELECT 520128009, 1, 1000, 520126009, 520123009, 520000303, 2.0000, 2180.00, 4360.00, 0.0000, NULL, 520000506, '2026-05-20 14:00:00', 520000506, '2026-05-20 14:00:00', 0
  UNION ALL SELECT 520128010, 1, 1000, 520126010, 520123010, 520000301, 10.0000, 1180.00, 11800.00, 0.0000, NULL, 520000506, '2026-06-10 14:00:00', 520000506, '2026-06-10 14:00:00', 0
  UNION ALL SELECT 520128011, 1, 1000, 520126011, 520123011, 520000304, 36.0000, 265.00, 9540.00, 0.0000, NULL, 520000506, '2026-06-12 14:00:00', 520000506, '2026-06-12 14:00:00', 0
  UNION ALL SELECT 520128012, 1, 1000, 520126012, 520123012, 520000306, 10.0000, 495.00, 4950.00, 0.0000, NULL, 520000506, '2026-06-15 14:00:00', 520000506, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520128013, 1, 1000, 520126013, 520123013, 520000302, 5.0000, 1580.00, 7900.00, 0.0000, NULL, 520000506, '2026-06-18 14:00:00', 520000506, '2026-06-18 14:00:00', 0
  UNION ALL SELECT 520128014, 1, 1000, 520126014, 520123014, 520000308, 3.0000, 780.00, 2340.00, 0.0000, NULL, 520000506, '2026-06-20 14:00:00', 520000506, '2026-06-20 14:00:00', 0
  UNION ALL SELECT 520128015, 1, 1000, 520126015, 520123015, 520000301, 4.0000, 1180.00, 4720.00, 0.0000, NULL, 520000506, '2026-07-10 14:00:00', 520000506, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520128016, 1, 1000, 520126016, 520123016, 520000304, 15.0000, 265.00, 3975.00, 0.0000, NULL, 520000506, '2026-07-13 14:00:00', 520000506, '2026-07-13 14:00:00', 0
  UNION ALL SELECT 520128017, 1, 1000, 520126017, 520123017, 520000307, 3.0000, 590.00, 1770.00, 0.0000, NULL, 520000506, '2026-07-15 14:00:00', 520000506, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520128018, 1, 1000, 520126018, 520123018, 520000301, 12.0000, 1180.00, 14160.00, 1.0000, NULL, 520000506, '2026-08-10 14:00:00', 520000506, '2026-08-10 14:00:00', 0
  UNION ALL SELECT 520128019, 1, 1000, 520126019, 520123019, 520000302, 8.0000, 1580.00, 12640.00, 0.0000, NULL, 520000506, '2026-08-12 14:00:00', 520000506, '2026-08-12 14:00:00', 0
  UNION ALL SELECT 520128020, 1, 1000, 520126020, 520123020, 520000304, 40.0000, 265.00, 10600.00, 0.0000, NULL, 520000506, '2026-08-14 14:00:00', 520000506, '2026-08-14 14:00:00', 0
  UNION ALL SELECT 520128021, 1, 1000, 520126021, 520123021, 520000305, 18.0000, 380.00, 6840.00, 0.0000, NULL, 520000506, '2026-08-15 14:00:00', 520000506, '2026-08-15 14:00:00', 0
  UNION ALL SELECT 520128022, 1, 1000, 520126022, 520123022, 520000306, 12.0000, 495.00, 5940.00, 0.0000, NULL, 520000506, '2026-08-18 14:00:00', 520000506, '2026-08-18 14:00:00', 0
  UNION ALL SELECT 520128023, 1, 1000, 520126023, 520123023, 520000308, 5.0000, 780.00, 3900.00, 0.0000, NULL, 520000506, '2026-08-20 14:00:00', 520000506, '2026-08-20 14:00:00', 0
  UNION ALL SELECT 520128024, 1, 1000, 520126024, 520123024, 520000301, 10.0000, 1180.00, 11800.00, 0.0000, NULL, 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520128025, 1, 1000, 520126025, 520123025, 520000302, 6.0000, 1580.00, 9480.00, 0.0000, NULL, 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520128026, 1, 1000, 520126026, 520123026, 520000304, 32.0000, 265.00, 8480.00, 0.0000, NULL, 520000506, '2026-09-03 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520128027, 1, 1000, 520126027, 520123027, 520000303, 3.0000, 2180.00, 6540.00, 0.0000, NULL, 520000506, '2026-09-03 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520128028, 1, 1000, 520126028, 520123028, 520000307, 8.0000, 590.00, 4720.00, 0.0000, NULL, 520000506, '2026-09-04 14:00:00', 520000506, '2026-09-04 14:00:00', 0
  UNION ALL SELECT 520128029, 1, 1000, 520126030, 520123030, 520000108, 50.0000, 0.90, 45.00, 0.0000, NULL, 1, '2026-09-06 13:16:35', 1, '2026-09-06 13:16:35', 0
  UNION ALL SELECT 520128030, 1, 1000, 520126031, 520123030, 520000108, 30.0000, 0.90, 27.00, 0.0000, NULL, 1, '2026-09-06 13:16:35', 1, '2026-09-06 13:16:35', 0
  UNION ALL SELECT 520128031, 1, 1000, 520126032, 520123030, 520000108, 5.0000, 0.90, 4.50, 0.0000, NULL, 1, '2026-09-06 13:16:52', 1, '2026-09-06 13:17:10', 1
  UNION ALL SELECT 520128032, 1, 1000, 520126033, 520123030, 520000108, 4.0000, 0.90, 3.60, 0.0000, NULL, 1, '2026-09-06 13:17:29', 1, '2026-09-06 13:17:29', 0
  UNION ALL SELECT 520128033, 1, 1000, 520126034, 520123030, 520000108, 6.0000, 0.90, 5.40, 0.0000, NULL, 1, '2026-09-06 13:17:30', 1, '2026-09-06 13:17:30', 0
  UNION ALL SELECT 520128034, 1, 1000, 520126035, 520123031, 520000107, 200.0000, 0.35, 70.00, 0.0000, NULL, 1, '2026-09-06 13:18:17', 1, '2026-09-06 13:18:17', 0
) _v WHERE @g = 0;

-- ---- sal_return : 销售退货单 (2行) ----
SET @g := (SELECT COUNT(*) FROM `sal_return` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sal_return` (`id`, `org_id`, `tenant_id`, `return_no`, `out_id`, `out_no`, `customer_id`, `warehouse_id`, `return_date`, `total_qty`, `total_amount`, `status`, `remark`, `audit_by`, `audit_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `return_no`, `out_id`, `out_no`, `customer_id`, `warehouse_id`, `return_date`, `total_qty`, `total_amount`, `status`, `remark`, `audit_by`, `audit_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520130001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'RT202605220001' AS `return_no`, 520126006 AS `out_id`, 'XS202605120001' AS `out_no`, 520000205 AS `customer_id`, 1 AS `warehouse_id`, '2026-05-22' AS `return_date`, 2 AS `total_qty`, 530.00 AS `total_amount`, 10 AS `status`, '客户装配尺寸超差退回' AS `remark`, 520000501 AS `audit_by`, '2026-05-22 16:00:00' AS `audit_time`, 520000506 AS `create_by`, '2026-05-21 15:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-05-22 15:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520130002, 1, 1000, 'RT202608220001', 520126018, 'XS202608100001', 520000201, 1, '2026-08-22', 1, 1180.00, 10, '运输磕碰漆面损伤退回', 520000501, '2026-08-22 16:00:00', 520000506, '2026-08-21 15:00:00', 520000506, '2026-08-22 15:00:00', 0
) _v WHERE @g = 0;

-- ---- sal_return_detail : 销售退货明细 (2行) ----
SET @g := (SELECT COUNT(*) FROM `sal_return_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `sal_return_detail` (`id`, `org_id`, `tenant_id`, `return_id`, `out_detail_id`, `material_id`, `qty`, `price`, `amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `return_id`, `out_detail_id`, `material_id`, `qty`, `price`, `amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520131001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520130001 AS `return_id`, 520128006 AS `out_detail_id`, 520000304 AS `material_id`, 2.0000 AS `qty`, 265.00 AS `price`, 530.00 AS `amount`, '??????????' AS `remark`, 520000506 AS `create_by`, '2026-05-22 15:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-05-22 15:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520131002, 1, 1000, 520130002, 520128018, 520000301, 1.0000, 1180.00, 1180.00, '??????????', 520000506, '2026-08-22 15:00:00', 520000506, '2026-08-22 15:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_work_order : 生产订单 (12行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_work_order` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_work_order` (`id`, `org_id`, `tenant_id`, `order_no`, `material_id`, `qty`, `bom_id`, `bom_version`, `warehouse_id`, `plan_start_date`, `plan_end_date`, `status`, `finish_qty`, `bad_qty`, `source_type`, `source_id`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `order_no`, `material_id`, `qty`, `bom_id`, `bom_version`, `warehouse_id`, `plan_start_date`, `plan_end_date`, `status`, `finish_qty`, `bad_qty`, `source_type`, `source_id`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520133001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'MO202604100001' AS `order_no`, 520000301 AS `material_id`, 10 AS `qty`, 520060001 AS `bom_id`, 'V1.0' AS `bom_version`, 1 AS `warehouse_id`, '2026-04-10' AS `plan_start_date`, '2026-04-28' AS `plan_end_date`, 'FINISHED' AS `status`, 10 AS `finish_qty`, 0 AS `bad_qty`, 'MANUAL' AS `source_type`, NULL AS `source_id`, '苏州精工演示工单' AS `remark`, 520000504 AS `create_by`, '2026-04-10 08:30:00' AS `create_time`, 520000504 AS `update_by`, '2026-04-28 16:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520133002, 1, 1000, 'MO202604180001', 520000304, 30, 520060004, 'V1.0', 1, '2026-04-18', '2026-04-30', 'FINISHED', 30, 0, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-04-18 08:30:00', 520000504, '2026-04-30 16:00:00', 0
  UNION ALL SELECT 520133003, 1, 1000, 'MO202605120001', 520000301, 12, 520060001, 'V1.0', 1, '2026-05-12', '2026-05-24', 'FINISHED', 12, 0, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-05-12 08:30:00', 520000504, '2026-05-24 16:00:00', 0
  UNION ALL SELECT 520133004, 1, 1000, 'MO202605190001', 520000304, 40, 520060004, 'V1.0', 1, '2026-05-19', '2026-05-26', 'FINISHED', 40, 0, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-05-19 08:30:00', 520000504, '2026-05-26 16:00:00', 0
  UNION ALL SELECT 520133005, 1, 1000, 'MO202606080001', 520000301, 12, 520060001, 'V1.0', 1, '2026-06-08', '2026-06-20', 'FINISHED', 12, 0, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-06-08 08:30:00', 520000504, '2026-06-20 16:00:00', 0
  UNION ALL SELECT 520133006, 1, 1000, 'MO202606110001', 520000304, 45, 520060004, 'V1.0', 1, '2026-06-11', '2026-06-18', 'FINISHED', 45, 2, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-06-11 08:30:00', 520000504, '2026-06-18 16:00:00', 0
  UNION ALL SELECT 520133007, 1, 1000, 'MO202607100001', 520000301, 6, 520060001, 'V1.0', 1, '2026-07-10', '2026-07-22', 'FINISHED', 6, 0, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-07-10 08:30:00', 520000504, '2026-07-22 16:00:00', 0
  UNION ALL SELECT 520133008, 1, 1000, 'MO202607140001', 520000304, 20, 520060004, 'V1.0', 1, '2026-07-14', '2026-07-24', 'FINISHED', 20, 0, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-07-14 08:30:00', 520000504, '2026-07-24 16:00:00', 0
  UNION ALL SELECT 520133009, 1, 1000, 'MO202608060001', 520000301, 14, 520060001, 'V1.0', 1, '2026-08-06', '2026-08-22', 'FINISHED', 14, 2, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-08-06 08:30:00', 520000504, '2026-08-22 16:00:00', 0
  UNION ALL SELECT 520133010, 1, 1000, 'MO202608030001', 520000304, 55, 520060004, 'V1.0', 1, '2026-08-03', '2026-08-20', 'FINISHED', 55, 0, 'MANUAL', NULL, '苏州精工演示工单', 520000504, '2026-08-03 08:30:00', 520000504, '2026-08-20 16:00:00', 0
  UNION ALL SELECT 520133011, 1, 1000, 'MO202608300001', 520000301, 10, 520060001, 'V1.0', 1, '2026-08-30', '2026-09-03', 'FINISHED', 10, 0, 'MRP_SUGGESTION', 520168001, '苏州精工演示工单', 520000504, '2026-08-30 08:30:00', 520000504, '2026-09-03 16:00:00', 0
  UNION ALL SELECT 520133012, 1, 1000, 'MO202609010001', 520000304, 30, 520060004, 'V1.0', 1, '2026-09-01', '2026-09-04', 'PRODUCING', 20, 0, 'MRP_SUGGESTION', 520168002, '苏州精工演示工单', 520000504, '2026-09-01 08:30:00', 520000504, '2026-09-04 16:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_wo_step : 工单工序任务 (42行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_wo_step` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_wo_step` (`id`, `org_id`, `tenant_id`, `work_order_id`, `seq_no`, `operation_id`, `work_center_id`, `unit_minutes`, `plan_qty`, `plan_start_time`, `plan_end_time`, `status`, `assignee`, `dispatch_time`, `finish_qty`, `bad_qty`, `actual_minutes`, `qc_pending_note`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `work_order_id`, `seq_no`, `operation_id`, `work_center_id`, `unit_minutes`, `plan_qty`, `plan_start_time`, `plan_end_time`, `status`, `assignee`, `dispatch_time`, `finish_qty`, `bad_qty`, `actual_minutes`, `qc_pending_note`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520135001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520133001 AS `work_order_id`, 1 AS `seq_no`, 520085004 AS `operation_id`, 520080003 AS `work_center_id`, 3 AS `unit_minutes`, 10 AS `plan_qty`, '2026-04-10 08:30:00' AS `plan_start_time`, '2026-04-10 09:00:00' AS `plan_end_time`, 'FINISHED' AS `status`, '陈立福' AS `assignee`, '2026-04-10 09:30:00' AS `dispatch_time`, 10 AS `finish_qty`, 0 AS `bad_qty`, 28 AS `actual_minutes`, NULL AS `qc_pending_note`, 520000504 AS `create_by`, '2026-04-10 08:30:00' AS `create_time`, 520000504 AS `update_by`, '2026-04-28 15:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520135002, 1, 1000, 520133001, 2, 520085005, 520080002, 25, 10, '2026-04-10 08:30:00', '2026-04-10 12:40:00', 'FINISHED', '陈立福', '2026-04-10 09:30:00', 10, 0, 240, NULL, 520000504, '2026-04-10 08:30:00', 520000504, '2026-04-28 15:30:00', 0
  UNION ALL SELECT 520135003, 1, 1000, 520133001, 3, 520085006, 520080003, 15, 10, '2026-04-10 08:30:00', '2026-04-10 11:00:00', 'FINISHED', '陈立福', '2026-04-10 09:30:00', 10, 0, 144, NULL, 520000504, '2026-04-10 08:30:00', 520000504, '2026-04-28 15:30:00', 0
  UNION ALL SELECT 520135004, 1, 1000, 520133001, 4, 520085007, 520080002, 5, 10, '2026-04-10 08:30:00', '2026-04-10 09:20:00', 'FINISHED', '陈立福', '2026-04-10 09:30:00', 10, 0, 48, NULL, 520000504, '2026-04-10 08:30:00', 520000504, '2026-04-28 15:30:00', 0
  UNION ALL SELECT 520135005, 1, 1000, 520133002, 1, 520085001, 520080001, 12, 30, '2026-04-18 08:30:00', '2026-04-18 14:30:00', 'FINISHED', '吴海', '2026-04-18 09:30:00', 30, 0, 345, NULL, 520000504, '2026-04-18 08:30:00', 520000504, '2026-04-30 15:30:00', 0
  UNION ALL SELECT 520135006, 1, 1000, 520133002, 2, 520085002, 520080001, 8, 30, '2026-04-18 08:30:00', '2026-04-18 12:30:00', 'FINISHED', '吴海', '2026-04-18 09:30:00', 30, 0, 230, NULL, 520000504, '2026-04-18 08:30:00', 520000504, '2026-04-30 15:30:00', 0
  UNION ALL SELECT 520135007, 1, 1000, 520133002, 3, 520085003, 520080003, 6, 30, '2026-04-18 08:30:00', '2026-04-18 11:30:00', 'FINISHED', '吴海', '2026-04-18 09:30:00', 30, 0, 172, NULL, 520000504, '2026-04-18 08:30:00', 520000504, '2026-04-30 15:30:00', 0
  UNION ALL SELECT 520135008, 1, 1000, 520133003, 1, 520085004, 520080003, 3, 12, '2026-05-12 08:30:00', '2026-05-12 09:06:00', 'FINISHED', '陈立福', '2026-05-12 09:30:00', 12, 0, 34, NULL, 520000504, '2026-05-12 08:30:00', 520000504, '2026-05-24 15:30:00', 0
  UNION ALL SELECT 520135009, 1, 1000, 520133003, 2, 520085005, 520080002, 25, 12, '2026-05-12 08:30:00', '2026-05-12 13:30:00', 'FINISHED', '陈立福', '2026-05-12 09:30:00', 12, 0, 288, NULL, 520000504, '2026-05-12 08:30:00', 520000504, '2026-05-24 15:30:00', 0
  UNION ALL SELECT 520135010, 1, 1000, 520133003, 3, 520085006, 520080003, 15, 12, '2026-05-12 08:30:00', '2026-05-12 11:30:00', 'FINISHED', '陈立福', '2026-05-12 09:30:00', 12, 0, 172, NULL, 520000504, '2026-05-12 08:30:00', 520000504, '2026-05-24 15:30:00', 0
  UNION ALL SELECT 520135011, 1, 1000, 520133003, 4, 520085007, 520080002, 5, 12, '2026-05-12 08:30:00', '2026-05-12 09:30:00', 'FINISHED', '陈立福', '2026-05-12 09:30:00', 12, 0, 57, NULL, 520000504, '2026-05-12 08:30:00', 520000504, '2026-05-24 15:30:00', 0
  UNION ALL SELECT 520135012, 1, 1000, 520133004, 1, 520085001, 520080001, 12, 40, '2026-05-19 08:30:00', '2026-05-19 16:30:00', 'FINISHED', '吴海', '2026-05-19 09:30:00', 40, 0, 460, NULL, 520000504, '2026-05-19 08:30:00', 520000504, '2026-05-26 15:30:00', 0
  UNION ALL SELECT 520135013, 1, 1000, 520133004, 2, 520085002, 520080001, 8, 40, '2026-05-19 08:30:00', '2026-05-19 13:50:00', 'FINISHED', '吴海', '2026-05-19 09:30:00', 40, 0, 307, NULL, 520000504, '2026-05-19 08:30:00', 520000504, '2026-05-26 15:30:00', 0
  UNION ALL SELECT 520135014, 1, 1000, 520133004, 3, 520085003, 520080003, 6, 40, '2026-05-19 08:30:00', '2026-05-19 12:30:00', 'FINISHED', '吴海', '2026-05-19 09:30:00', 40, 0, 230, NULL, 520000504, '2026-05-19 08:30:00', 520000504, '2026-05-26 15:30:00', 0
  UNION ALL SELECT 520135015, 1, 1000, 520133005, 1, 520085004, 520080003, 3, 12, '2026-06-08 08:30:00', '2026-06-08 09:06:00', 'FINISHED', '陈立福', '2026-06-08 09:30:00', 12, 0, 34, NULL, 520000504, '2026-06-08 08:30:00', 520000504, '2026-06-20 15:30:00', 0
  UNION ALL SELECT 520135016, 1, 1000, 520133005, 2, 520085005, 520080002, 25, 12, '2026-06-08 08:30:00', '2026-06-08 13:30:00', 'FINISHED', '陈立福', '2026-06-08 09:30:00', 12, 0, 288, NULL, 520000504, '2026-06-08 08:30:00', 520000504, '2026-06-20 15:30:00', 0
  UNION ALL SELECT 520135017, 1, 1000, 520133005, 3, 520085006, 520080003, 15, 12, '2026-06-08 08:30:00', '2026-06-08 11:30:00', 'FINISHED', '陈立福', '2026-06-08 09:30:00', 12, 0, 172, NULL, 520000504, '2026-06-08 08:30:00', 520000504, '2026-06-20 15:30:00', 0
  UNION ALL SELECT 520135018, 1, 1000, 520133005, 4, 520085007, 520080002, 5, 12, '2026-06-08 08:30:00', '2026-06-08 09:30:00', 'FINISHED', '陈立福', '2026-06-08 09:30:00', 12, 0, 57, NULL, 520000504, '2026-06-08 08:30:00', 520000504, '2026-06-20 15:30:00', 0
  UNION ALL SELECT 520135019, 1, 1000, 520133006, 1, 520085001, 520080001, 12, 45, '2026-06-11 08:30:00', '2026-06-11 17:30:00', 'FINISHED', '吴海', '2026-06-11 09:30:00', 45, 0, 518, NULL, 520000504, '2026-06-11 08:30:00', 520000504, '2026-06-18 15:30:00', 0
  UNION ALL SELECT 520135020, 1, 1000, 520133006, 2, 520085002, 520080001, 8, 45, '2026-06-11 08:30:00', '2026-06-11 14:30:00', 'FINISHED', '吴海', '2026-06-11 09:30:00', 45, 0, 345, NULL, 520000504, '2026-06-11 08:30:00', 520000504, '2026-06-18 15:30:00', 0
  UNION ALL SELECT 520135021, 1, 1000, 520133006, 3, 520085003, 520080003, 6, 45, '2026-06-11 08:30:00', '2026-06-11 13:00:00', 'FINISHED', '吴海', '2026-06-11 09:30:00', 45, 2, 259, 'FQC发现不良2件,待处置', 520000504, '2026-06-11 08:30:00', 520000504, '2026-06-18 15:30:00', 0
  UNION ALL SELECT 520135022, 1, 1000, 520133007, 1, 520085004, 520080003, 3, 6, '2026-07-10 08:30:00', '2026-07-10 08:48:00', 'FINISHED', '陈立福', '2026-07-10 09:30:00', 6, 0, 17, NULL, 520000504, '2026-07-10 08:30:00', 520000504, '2026-07-22 15:30:00', 0
  UNION ALL SELECT 520135023, 1, 1000, 520133007, 2, 520085005, 520080002, 25, 6, '2026-07-10 08:30:00', '2026-07-10 11:00:00', 'FINISHED', '陈立福', '2026-07-10 09:30:00', 6, 0, 144, NULL, 520000504, '2026-07-10 08:30:00', 520000504, '2026-07-22 15:30:00', 0
  UNION ALL SELECT 520135024, 1, 1000, 520133007, 3, 520085006, 520080003, 15, 6, '2026-07-10 08:30:00', '2026-07-10 10:00:00', 'FINISHED', '陈立福', '2026-07-10 09:30:00', 6, 0, 86, NULL, 520000504, '2026-07-10 08:30:00', 520000504, '2026-07-22 15:30:00', 0
  UNION ALL SELECT 520135025, 1, 1000, 520133007, 4, 520085007, 520080002, 5, 6, '2026-07-10 08:30:00', '2026-07-10 09:00:00', 'FINISHED', '陈立福', '2026-07-10 09:30:00', 6, 0, 28, NULL, 520000504, '2026-07-10 08:30:00', 520000504, '2026-07-22 15:30:00', 0
  UNION ALL SELECT 520135026, 1, 1000, 520133008, 1, 520085001, 520080001, 12, 20, '2026-07-14 08:30:00', '2026-07-14 12:30:00', 'FINISHED', '吴海', '2026-07-14 09:30:00', 20, 0, 230, NULL, 520000504, '2026-07-14 08:30:00', 520000504, '2026-07-24 15:30:00', 0
  UNION ALL SELECT 520135027, 1, 1000, 520133008, 2, 520085002, 520080001, 8, 20, '2026-07-14 08:30:00', '2026-07-14 11:10:00', 'FINISHED', '吴海', '2026-07-14 09:30:00', 20, 0, 153, NULL, 520000504, '2026-07-14 08:30:00', 520000504, '2026-07-24 15:30:00', 0
  UNION ALL SELECT 520135028, 1, 1000, 520133008, 3, 520085003, 520080003, 6, 20, '2026-07-14 08:30:00', '2026-07-14 10:30:00', 'FINISHED', '吴海', '2026-07-14 09:30:00', 20, 0, 115, NULL, 520000504, '2026-07-14 08:30:00', 520000504, '2026-07-24 15:30:00', 0
  UNION ALL SELECT 520135029, 1, 1000, 520133009, 1, 520085004, 520080003, 3, 14, '2026-08-06 08:30:00', '2026-08-06 09:12:00', 'FINISHED', '陈立福', '2026-08-06 09:30:00', 14, 0, 40, NULL, 520000504, '2026-08-06 08:30:00', 520000504, '2026-08-22 15:30:00', 0
  UNION ALL SELECT 520135030, 1, 1000, 520133009, 2, 520085005, 520080002, 25, 14, '2026-08-06 08:30:00', '2026-08-06 14:20:00', 'FINISHED', '陈立福', '2026-08-06 09:30:00', 14, 0, 336, NULL, 520000504, '2026-08-06 08:30:00', 520000504, '2026-08-22 15:30:00', 0
  UNION ALL SELECT 520135031, 1, 1000, 520133009, 3, 520085006, 520080003, 15, 14, '2026-08-06 08:30:00', '2026-08-06 12:00:00', 'FINISHED', '陈立福', '2026-08-06 09:30:00', 14, 0, 201, NULL, 520000504, '2026-08-06 08:30:00', 520000504, '2026-08-22 15:30:00', 0
  UNION ALL SELECT 520135032, 1, 1000, 520133009, 4, 520085007, 520080002, 5, 14, '2026-08-06 08:30:00', '2026-08-06 09:40:00', 'FINISHED', '陈立福', '2026-08-06 09:30:00', 14, 2, 67, 'FQC发现不良2件,待处置', 520000504, '2026-08-06 08:30:00', 520000504, '2026-08-22 15:30:00', 0
  UNION ALL SELECT 520135033, 1, 1000, 520133010, 1, 520085001, 520080001, 12, 55, '2026-08-03 08:30:00', '2026-08-03 19:30:00', 'FINISHED', '吴海', '2026-08-03 09:30:00', 55, 0, 633, NULL, 520000504, '2026-08-03 08:30:00', 520000504, '2026-08-20 15:30:00', 0
  UNION ALL SELECT 520135034, 1, 1000, 520133010, 2, 520085002, 520080001, 8, 55, '2026-08-03 08:30:00', '2026-08-03 15:50:00', 'FINISHED', '吴海', '2026-08-03 09:30:00', 55, 0, 422, NULL, 520000504, '2026-08-03 08:30:00', 520000504, '2026-08-20 15:30:00', 0
  UNION ALL SELECT 520135035, 1, 1000, 520133010, 3, 520085003, 520080003, 6, 55, '2026-08-03 08:30:00', '2026-08-03 14:00:00', 'FINISHED', '吴海', '2026-08-03 09:30:00', 55, 0, 316, NULL, 520000504, '2026-08-03 08:30:00', 520000504, '2026-08-20 15:30:00', 0
  UNION ALL SELECT 520135036, 1, 1000, 520133011, 1, 520085004, 520080003, 3, 10, '2026-08-30 08:30:00', '2026-08-30 09:00:00', 'FINISHED', '陈立福', '2026-08-30 09:30:00', 10, 0, 28, NULL, 520000504, '2026-08-30 08:30:00', 520000504, '2026-09-03 15:30:00', 0
  UNION ALL SELECT 520135037, 1, 1000, 520133011, 2, 520085005, 520080002, 25, 10, '2026-08-30 08:30:00', '2026-08-30 12:40:00', 'FINISHED', '陈立福', '2026-08-30 09:30:00', 10, 0, 240, NULL, 520000504, '2026-08-30 08:30:00', 520000504, '2026-09-03 15:30:00', 0
  UNION ALL SELECT 520135038, 1, 1000, 520133011, 3, 520085006, 520080003, 15, 10, '2026-08-30 08:30:00', '2026-08-30 11:00:00', 'FINISHED', '陈立福', '2026-08-30 09:30:00', 10, 0, 144, NULL, 520000504, '2026-08-30 08:30:00', 520000504, '2026-09-03 15:30:00', 0
  UNION ALL SELECT 520135039, 1, 1000, 520133011, 4, 520085007, 520080002, 5, 10, '2026-08-30 08:30:00', '2026-08-30 09:20:00', 'FINISHED', '陈立福', '2026-08-30 09:30:00', 10, 0, 48, NULL, 520000504, '2026-08-30 08:30:00', 520000504, '2026-09-03 15:30:00', 0
  UNION ALL SELECT 520135040, 1, 1000, 520133012, 1, 520085001, 520080001, 12, 30, '2026-09-01 08:30:00', '2026-09-01 14:30:00', 'FINISHED', '吴海', '2026-09-01 09:30:00', 30, 0, 345, NULL, 520000504, '2026-09-01 08:30:00', 520000504, '2026-09-04 15:30:00', 0
  UNION ALL SELECT 520135041, 1, 1000, 520133012, 2, 520085002, 520080001, 8, 30, '2026-09-01 08:30:00', '2026-09-01 12:30:00', 'FINISHED', '吴海', '2026-09-01 09:30:00', 30, 0, 230, NULL, 520000504, '2026-09-01 08:30:00', 520000504, '2026-09-04 15:30:00', 0
  UNION ALL SELECT 520135042, 1, 1000, 520133012, 3, 520085003, 520080003, 6, 30, '2026-09-01 08:30:00', '2026-09-01 11:30:00', 'PROCESSING', '吴海', '2026-09-01 09:30:00', 20, 0, 172, NULL, 520000504, '2026-09-01 08:30:00', 520000504, '2026-09-04 15:30:00', 0
) _v WHERE @g = 0;

-- ---- mrp_work_report : 工序报工 (12行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_work_report` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_work_report` (`id`, `org_id`, `tenant_id`, `wo_step_id`, `work_order_id`, `operation_id`, `employee`, `good_qty`, `bad_qty`, `work_minutes`, `qc_note`, `report_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `wo_step_id`, `work_order_id`, `operation_id`, `employee`, `good_qty`, `bad_qty`, `work_minutes`, `qc_note`, `report_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520136001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520135004 AS `wo_step_id`, 520133001 AS `work_order_id`, 520085007 AS `operation_id`, '周建国' AS `employee`, 10 AS `good_qty`, 0 AS `bad_qty`, 460 AS `work_minutes`, NULL AS `qc_note`, '2026-04-28 15:30:00' AS `report_time`, 520000504 AS `create_by`, '2026-04-28 15:30:00' AS `create_time`, 520000504 AS `update_by`, '2026-04-28 15:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520136002, 1, 1000, 520135007, 520133002, 520085003, '周建国', 30, 0, 748, NULL, '2026-04-30 15:30:00', 520000504, '2026-04-30 15:30:00', 520000504, '2026-04-30 15:30:00', 0
  UNION ALL SELECT 520136003, 1, 1000, 520135011, 520133003, 520085007, '周建国', 12, 0, 552, NULL, '2026-05-24 15:30:00', 520000504, '2026-05-24 15:30:00', 520000504, '2026-05-24 15:30:00', 0
  UNION ALL SELECT 520136004, 1, 1000, 520135014, 520133004, 520085003, '周建国', 40, 0, 998, NULL, '2026-05-26 15:30:00', 520000504, '2026-05-26 15:30:00', 520000504, '2026-05-26 15:30:00', 0
  UNION ALL SELECT 520136005, 1, 1000, 520135018, 520133005, 520085007, '周建国', 12, 0, 552, NULL, '2026-06-20 15:30:00', 520000504, '2026-06-20 15:30:00', 520000504, '2026-06-20 15:30:00', 0
  UNION ALL SELECT 520136006, 1, 1000, 520135021, 520133006, 520085003, '周建国', 45, 2, 1123, '不良2件待处置', '2026-06-18 15:30:00', 520000504, '2026-06-18 15:30:00', 520000504, '2026-06-18 15:30:00', 0
  UNION ALL SELECT 520136007, 1, 1000, 520135025, 520133007, 520085007, '周建国', 6, 0, 276, NULL, '2026-07-22 15:30:00', 520000504, '2026-07-22 15:30:00', 520000504, '2026-07-22 15:30:00', 0
  UNION ALL SELECT 520136008, 1, 1000, 520135028, 520133008, 520085003, '周建国', 20, 0, 499, NULL, '2026-07-24 15:30:00', 520000504, '2026-07-24 15:30:00', 520000504, '2026-07-24 15:30:00', 0
  UNION ALL SELECT 520136009, 1, 1000, 520135032, 520133009, 520085007, '周建国', 14, 2, 645, '不良2件待处置', '2026-08-22 15:30:00', 520000504, '2026-08-22 15:30:00', 520000504, '2026-08-22 15:30:00', 0
  UNION ALL SELECT 520136010, 1, 1000, 520135035, 520133010, 520085003, '周建国', 55, 0, 1372, NULL, '2026-08-20 15:30:00', 520000504, '2026-08-20 15:30:00', 520000504, '2026-08-20 15:30:00', 0
  UNION ALL SELECT 520136011, 1, 1000, 520135039, 520133011, 520085007, '周建国', 10, 0, 460, NULL, '2026-09-03 15:30:00', 520000504, '2026-09-03 15:30:00', 520000504, '2026-09-03 15:30:00', 0
  UNION ALL SELECT 520136012, 1, 1000, 520135042, 520133012, 520085003, '周建国', 20, 0, 499, NULL, '2026-09-04 15:30:00', 520000504, '2026-09-04 15:30:00', 520000504, '2026-09-04 15:30:00', 0
) _v WHERE @g = 0;

-- ---- mrp_material_issue : 生产领料单 (12行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_material_issue` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_material_issue` (`id`, `org_id`, `tenant_id`, `issue_no`, `work_order_id`, `warehouse_id`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `issue_no`, `work_order_id`, `warehouse_id`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520137001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'MI202604150001' AS `issue_no`, 520133001 AS `work_order_id`, 1 AS `warehouse_id`, 'AUDITED' AS `status`, 'BOM按单领料' AS `remark`, 520000504 AS `create_by`, '2026-04-15 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-04-15 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520137002, 1, 1000, 'MI202604200001', 520133002, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-04-20 09:00:00', 520000504, '2026-04-20 09:00:00', 0
  UNION ALL SELECT 520137003, 1, 1000, 'MI202605150001', 520133003, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-05-15 09:00:00', 520000504, '2026-05-15 09:00:00', 0
  UNION ALL SELECT 520137004, 1, 1000, 'MI202605210001', 520133004, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-05-21 09:00:00', 520000504, '2026-05-21 09:00:00', 0
  UNION ALL SELECT 520137005, 1, 1000, 'MI202606100001', 520133005, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-06-10 09:00:00', 520000504, '2026-06-10 09:00:00', 0
  UNION ALL SELECT 520137006, 1, 1000, 'MI202606140001', 520133006, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-06-14 09:00:00', 520000504, '2026-06-14 09:00:00', 0
  UNION ALL SELECT 520137007, 1, 1000, 'MI202607130001', 520133007, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-07-13 09:00:00', 520000504, '2026-07-13 09:00:00', 0
  UNION ALL SELECT 520137008, 1, 1000, 'MI202607160001', 520133008, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-07-16 09:00:00', 520000504, '2026-07-16 09:00:00', 0
  UNION ALL SELECT 520137009, 1, 1000, 'MI202608100001', 520133009, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-08-10 09:00:00', 520000504, '2026-08-10 09:00:00', 0
  UNION ALL SELECT 520137010, 1, 1000, 'MI202608130001', 520133010, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-08-13 09:00:00', 520000504, '2026-08-13 09:00:00', 0
  UNION ALL SELECT 520137011, 1, 1000, 'MI202609010001', 520133011, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-09-01 09:00:00', 520000504, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520137012, 1, 1000, 'MI202609020001', 520133012, 1, 'AUDITED', 'BOM按单领料', 520000504, '2026-09-02 09:00:00', 520000504, '2026-09-02 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_material_issue_item : 生产领料明细 (60行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_material_issue_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_material_issue_item` (`id`, `org_id`, `tenant_id`, `issue_id`, `material_id`, `should_qty`, `actual_qty`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `issue_id`, `material_id`, `should_qty`, `actual_qty`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520138001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520137001 AS `issue_id`, 520000201 AS `material_id`, 10.2000 AS `should_qty`, 10.0000 AS `actual_qty`, 520000504 AS `create_by`, '2026-04-15 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-04-15 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520138002, 1, 1000, 520137001, 520000202, 10.1000, 10.0000, 520000504, '2026-04-15 09:00:00', 520000504, '2026-04-15 09:00:00', 0
  UNION ALL SELECT 520138003, 1, 1000, 520137001, 520000109, 20.0000, 20.0000, 520000504, '2026-04-15 09:00:00', 520000504, '2026-04-15 09:00:00', 0
  UNION ALL SELECT 520138004, 1, 1000, 520137001, 520000106, 120.0000, 120.0000, 520000504, '2026-04-15 09:00:00', 520000504, '2026-04-15 09:00:00', 0
  UNION ALL SELECT 520138005, 1, 1000, 520137001, 520000403, 10.0000, 10.0000, 520000504, '2026-04-15 09:00:00', 520000504, '2026-04-15 09:00:00', 0
  UNION ALL SELECT 520138006, 1, 1000, 520137001, 520000405, 20.0000, 20.0000, 520000504, '2026-04-15 09:00:00', 520000504, '2026-04-15 09:00:00', 0
  UNION ALL SELECT 520138007, 1, 1000, 520137002, 520000203, 30.6000, 30.0000, 520000504, '2026-04-20 09:00:00', 520000504, '2026-04-20 09:00:00', 0
  UNION ALL SELECT 520138008, 1, 1000, 520137002, 520000105, 120.0000, 120.0000, 520000504, '2026-04-20 09:00:00', 520000504, '2026-04-20 09:00:00', 0
  UNION ALL SELECT 520138009, 1, 1000, 520137002, 520000110, 60.6000, 60.0000, 520000504, '2026-04-20 09:00:00', 520000504, '2026-04-20 09:00:00', 0
  UNION ALL SELECT 520138010, 1, 1000, 520137002, 520000404, 30.0000, 30.0000, 520000504, '2026-04-20 09:00:00', 520000504, '2026-04-20 09:00:00', 0
  UNION ALL SELECT 520138011, 1, 1000, 520137003, 520000201, 12.2400, 12.0000, 520000504, '2026-05-15 09:00:00', 520000504, '2026-05-15 09:00:00', 0
  UNION ALL SELECT 520138012, 1, 1000, 520137003, 520000202, 12.1200, 12.0000, 520000504, '2026-05-15 09:00:00', 520000504, '2026-05-15 09:00:00', 0
  UNION ALL SELECT 520138013, 1, 1000, 520137003, 520000109, 24.0000, 24.0000, 520000504, '2026-05-15 09:00:00', 520000504, '2026-05-15 09:00:00', 0
  UNION ALL SELECT 520138014, 1, 1000, 520137003, 520000106, 144.0000, 144.0000, 520000504, '2026-05-15 09:00:00', 520000504, '2026-05-15 09:00:00', 0
  UNION ALL SELECT 520138015, 1, 1000, 520137003, 520000403, 12.0000, 12.0000, 520000504, '2026-05-15 09:00:00', 520000504, '2026-05-15 09:00:00', 0
  UNION ALL SELECT 520138016, 1, 1000, 520137003, 520000405, 24.0000, 24.0000, 520000504, '2026-05-15 09:00:00', 520000504, '2026-05-15 09:00:00', 0
  UNION ALL SELECT 520138017, 1, 1000, 520137004, 520000203, 40.8000, 40.0000, 520000504, '2026-05-21 09:00:00', 520000504, '2026-05-21 09:00:00', 0
  UNION ALL SELECT 520138018, 1, 1000, 520137004, 520000105, 160.0000, 160.0000, 520000504, '2026-05-21 09:00:00', 520000504, '2026-05-21 09:00:00', 0
  UNION ALL SELECT 520138019, 1, 1000, 520137004, 520000110, 80.8000, 80.0000, 520000504, '2026-05-21 09:00:00', 520000504, '2026-05-21 09:00:00', 0
  UNION ALL SELECT 520138020, 1, 1000, 520137004, 520000404, 40.0000, 40.0000, 520000504, '2026-05-21 09:00:00', 520000504, '2026-05-21 09:00:00', 0
  UNION ALL SELECT 520138021, 1, 1000, 520137005, 520000201, 12.2400, 12.0000, 520000504, '2026-06-10 09:00:00', 520000504, '2026-06-10 09:00:00', 0
  UNION ALL SELECT 520138022, 1, 1000, 520137005, 520000202, 12.1200, 12.0000, 520000504, '2026-06-10 09:00:00', 520000504, '2026-06-10 09:00:00', 0
  UNION ALL SELECT 520138023, 1, 1000, 520137005, 520000109, 24.0000, 24.0000, 520000504, '2026-06-10 09:00:00', 520000504, '2026-06-10 09:00:00', 0
  UNION ALL SELECT 520138024, 1, 1000, 520137005, 520000106, 144.0000, 144.0000, 520000504, '2026-06-10 09:00:00', 520000504, '2026-06-10 09:00:00', 0
  UNION ALL SELECT 520138025, 1, 1000, 520137005, 520000403, 12.0000, 12.0000, 520000504, '2026-06-10 09:00:00', 520000504, '2026-06-10 09:00:00', 0
  UNION ALL SELECT 520138026, 1, 1000, 520137005, 520000405, 24.0000, 24.0000, 520000504, '2026-06-10 09:00:00', 520000504, '2026-06-10 09:00:00', 0
  UNION ALL SELECT 520138027, 1, 1000, 520137006, 520000203, 45.9000, 45.0000, 520000504, '2026-06-14 09:00:00', 520000504, '2026-06-14 09:00:00', 0
  UNION ALL SELECT 520138028, 1, 1000, 520137006, 520000105, 180.0000, 180.0000, 520000504, '2026-06-14 09:00:00', 520000504, '2026-06-14 09:00:00', 0
  UNION ALL SELECT 520138029, 1, 1000, 520137006, 520000110, 90.9000, 90.0000, 520000504, '2026-06-14 09:00:00', 520000504, '2026-06-14 09:00:00', 0
  UNION ALL SELECT 520138030, 1, 1000, 520137006, 520000404, 45.0000, 45.0000, 520000504, '2026-06-14 09:00:00', 520000504, '2026-06-14 09:00:00', 0
  UNION ALL SELECT 520138031, 1, 1000, 520137007, 520000201, 6.1200, 6.0000, 520000504, '2026-07-13 09:00:00', 520000504, '2026-07-13 09:00:00', 0
  UNION ALL SELECT 520138032, 1, 1000, 520137007, 520000202, 6.0600, 6.0000, 520000504, '2026-07-13 09:00:00', 520000504, '2026-07-13 09:00:00', 0
  UNION ALL SELECT 520138033, 1, 1000, 520137007, 520000109, 12.0000, 12.0000, 520000504, '2026-07-13 09:00:00', 520000504, '2026-07-13 09:00:00', 0
  UNION ALL SELECT 520138034, 1, 1000, 520137007, 520000106, 72.0000, 72.0000, 520000504, '2026-07-13 09:00:00', 520000504, '2026-07-13 09:00:00', 0
  UNION ALL SELECT 520138035, 1, 1000, 520137007, 520000403, 6.0000, 6.0000, 520000504, '2026-07-13 09:00:00', 520000504, '2026-07-13 09:00:00', 0
  UNION ALL SELECT 520138036, 1, 1000, 520137007, 520000405, 12.0000, 12.0000, 520000504, '2026-07-13 09:00:00', 520000504, '2026-07-13 09:00:00', 0
  UNION ALL SELECT 520138037, 1, 1000, 520137008, 520000203, 20.4000, 20.0000, 520000504, '2026-07-16 09:00:00', 520000504, '2026-07-16 09:00:00', 0
  UNION ALL SELECT 520138038, 1, 1000, 520137008, 520000105, 80.0000, 80.0000, 520000504, '2026-07-16 09:00:00', 520000504, '2026-07-16 09:00:00', 0
  UNION ALL SELECT 520138039, 1, 1000, 520137008, 520000110, 40.4000, 40.0000, 520000504, '2026-07-16 09:00:00', 520000504, '2026-07-16 09:00:00', 0
  UNION ALL SELECT 520138040, 1, 1000, 520137008, 520000404, 20.0000, 20.0000, 520000504, '2026-07-16 09:00:00', 520000504, '2026-07-16 09:00:00', 0
  UNION ALL SELECT 520138041, 1, 1000, 520137009, 520000201, 14.2800, 14.0000, 520000504, '2026-08-10 09:00:00', 520000504, '2026-08-10 09:00:00', 0
  UNION ALL SELECT 520138042, 1, 1000, 520137009, 520000202, 14.1400, 14.0000, 520000504, '2026-08-10 09:00:00', 520000504, '2026-08-10 09:00:00', 0
  UNION ALL SELECT 520138043, 1, 1000, 520137009, 520000109, 28.0000, 28.0000, 520000504, '2026-08-10 09:00:00', 520000504, '2026-08-10 09:00:00', 0
  UNION ALL SELECT 520138044, 1, 1000, 520137009, 520000106, 168.0000, 168.0000, 520000504, '2026-08-10 09:00:00', 520000504, '2026-08-10 09:00:00', 0
  UNION ALL SELECT 520138045, 1, 1000, 520137009, 520000403, 14.0000, 14.0000, 520000504, '2026-08-10 09:00:00', 520000504, '2026-08-10 09:00:00', 0
  UNION ALL SELECT 520138046, 1, 1000, 520137009, 520000405, 28.0000, 28.0000, 520000504, '2026-08-10 09:00:00', 520000504, '2026-08-10 09:00:00', 0
  UNION ALL SELECT 520138047, 1, 1000, 520137010, 520000203, 56.1000, 55.0000, 520000504, '2026-08-13 09:00:00', 520000504, '2026-08-13 09:00:00', 0
  UNION ALL SELECT 520138048, 1, 1000, 520137010, 520000105, 220.0000, 220.0000, 520000504, '2026-08-13 09:00:00', 520000504, '2026-08-13 09:00:00', 0
  UNION ALL SELECT 520138049, 1, 1000, 520137010, 520000110, 111.1000, 110.0000, 520000504, '2026-08-13 09:00:00', 520000504, '2026-08-13 09:00:00', 0
  UNION ALL SELECT 520138050, 1, 1000, 520137010, 520000404, 55.0000, 55.0000, 520000504, '2026-08-13 09:00:00', 520000504, '2026-08-13 09:00:00', 0
  UNION ALL SELECT 520138051, 1, 1000, 520137011, 520000201, 10.2000, 10.0000, 520000504, '2026-09-01 09:00:00', 520000504, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520138052, 1, 1000, 520137011, 520000202, 10.1000, 10.0000, 520000504, '2026-09-01 09:00:00', 520000504, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520138053, 1, 1000, 520137011, 520000109, 20.0000, 20.0000, 520000504, '2026-09-01 09:00:00', 520000504, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520138054, 1, 1000, 520137011, 520000106, 120.0000, 120.0000, 520000504, '2026-09-01 09:00:00', 520000504, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520138055, 1, 1000, 520137011, 520000403, 10.0000, 10.0000, 520000504, '2026-09-01 09:00:00', 520000504, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520138056, 1, 1000, 520137011, 520000405, 20.0000, 20.0000, 520000504, '2026-09-01 09:00:00', 520000504, '2026-09-01 09:00:00', 0
  UNION ALL SELECT 520138057, 1, 1000, 520137012, 520000203, 30.6000, 30.0000, 520000504, '2026-09-02 09:00:00', 520000504, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520138058, 1, 1000, 520137012, 520000105, 120.0000, 120.0000, 520000504, '2026-09-02 09:00:00', 520000504, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520138059, 1, 1000, 520137012, 520000110, 60.6000, 60.0000, 520000504, '2026-09-02 09:00:00', 520000504, '2026-09-02 09:00:00', 0
  UNION ALL SELECT 520138060, 1, 1000, 520137012, 520000404, 30.0000, 30.0000, 520000504, '2026-09-02 09:00:00', 520000504, '2026-09-02 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_outsource : 委外加工单 (1行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_outsource` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_outsource` (`id`, `org_id`, `tenant_id`, `order_no`, `supplier_id`, `material_id`, `qty`, `bom_id`, `bom_version`, `issue_warehouse_id`, `in_warehouse_id`, `fee_unit_price`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `order_no`, `supplier_id`, `material_id`, `qty`, `bom_id`, `bom_version`, `issue_warehouse_id`, `in_warehouse_id`, `fee_unit_price`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520139001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'OS202606100001' AS `order_no`, 520000306 AS `supplier_id`, 520000304 AS `material_id`, 40 AS `qty`, 520060004 AS `bom_id`, 'V1.0' AS `bom_version`, 1 AS `issue_warehouse_id`, 1 AS `in_warehouse_id`, 12.00 AS `fee_unit_price`, 'RECEIVED' AS `status`, '传动轴热处理工序委外加工' AS `remark`, 520000504 AS `create_by`, '2026-06-10 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-06-25 11:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- mrp_outsource_item : 委外发料明细 (4行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_outsource_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_outsource_item` (`id`, `org_id`, `tenant_id`, `outsource_id`, `material_id`, `qty`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `outsource_id`, `material_id`, `qty`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520140001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520139001 AS `outsource_id`, 520000203 AS `material_id`, 40.0000 AS `qty`, 520000504 AS `create_by`, '2026-06-15 09:00:00' AS `create_time`, 520000504 AS `update_by`, '2026-06-15 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520140002, 1, 1000, 520139001, 520000105, 160.0000, 520000504, '2026-06-15 09:00:00', 520000504, '2026-06-15 09:00:00', 0
  UNION ALL SELECT 520140003, 1, 1000, 520139001, 520000110, 80.0000, 520000504, '2026-06-15 09:00:00', 520000504, '2026-06-15 09:00:00', 0
  UNION ALL SELECT 520140004, 1, 1000, 520139001, 520000404, 40.0000, 520000504, '2026-06-15 09:00:00', 520000504, '2026-06-15 09:00:00', 0
) _v WHERE @g = 0;

-- ---- mrp_mps : MPS主生产计划 (2行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_mps` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_mps` (`id`, `org_id`, `tenant_id`, `material_id`, `qty`, `start_date`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `material_id`, `qty`, `start_date`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520165001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520000301 AS `material_id`, 60 AS `qty`, '2026-09-15' AS `start_date`, 'CALCULATED' AS `status`, '9月中旬交货(建议已转工单)' AS `remark`, 520000504 AS `create_by`, '2026-08-28 17:30:00' AS `create_time`, 520000504 AS `update_by`, '2026-08-28 17:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520165002, 1, 1000, 520000304, 200, '2026-09-20', 'CALCULATED', '华东大客户紧急订单', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
) _v WHERE @g = 0;

-- ---- mrp_calc : MRP运算批次 (1行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_calc` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_calc` (`id`, `org_id`, `tenant_id`, `batch_no`, `mps_count`, `suggestion_count`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `batch_no`, `mps_count`, `suggestion_count`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520166001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'MR202608280001' AS `batch_no`, 2 AS `mps_count`, 4 AS `suggestion_count`, '按BOM全级展开运算,2条生产建议已转9月工单' AS `remark`, 520000504 AS `create_by`, '2026-08-28 17:30:00' AS `create_time`, 520000504 AS `update_by`, '2026-08-28 17:30:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- mrp_calc_detail : MRP运算明细 (16行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_calc_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_calc_detail` (`id`, `org_id`, `tenant_id`, `batch_no`, `mps_id`, `level`, `material_id`, `gross_qty`, `stock_qty`, `onway_qty`, `net_qty`, `suggest_qty`, `suggest_date`, `plan_type`, `node_path`, `parent_path`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `batch_no`, `mps_id`, `level`, `material_id`, `gross_qty`, `stock_qty`, `onway_qty`, `net_qty`, `suggest_qty`, `suggest_date`, `plan_type`, `node_path`, `parent_path`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520167001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'MR202608280001' AS `batch_no`, 520165001 AS `mps_id`, 0 AS `level`, 520000301 AS `material_id`, 60.0000 AS `gross_qty`, 34.0000 AS `stock_qty`, 0 AS `onway_qty`, 26.0000 AS `net_qty`, 26.0000 AS `suggest_qty`, '2026-09-08' AS `suggest_date`, 'PRODUCE' AS `plan_type`, '116' AS `node_path`, '116' AS `parent_path`, 520000504 AS `create_by`, '2026-08-28 17:30:00' AS `create_time`, 520000504 AS `update_by`, '2026-08-28 17:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520167002, 1, 1000, 'MR202608280001', 520165001, 1, 520000201, 26.0000, 46.0000, 0, 0.0000, NULL, '2026-09-08', NULL, '116,201', '116', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167003, 1, 1000, 'MR202608280001', 520165001, 1, 520000202, 26.0000, 26.0000, 0, 0.0000, NULL, '2026-09-08', NULL, '116,202', '116', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167004, 1, 1000, 'MR202608280001', 520165001, 1, 520000109, 52.0000, 882.0000, 0, 0.0000, NULL, '2026-09-08', NULL, '116,109', '116', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167005, 1, 1000, 'MR202608280001', 520165001, 1, 520000106, 312.0000, 59152.0000, 0, 0.0000, NULL, '2026-09-08', NULL, '116,106', '116', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167006, 1, 1000, 'MR202608280001', 520165001, 1, 520000403, 26.0000, 746.0000, 0, 0.0000, NULL, '2026-09-08', NULL, '116,403', '116', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167007, 1, 1000, 'MR202608280001', 520165001, 1, 520000405, 52.0000, 1887.0000, 0, 0.0000, NULL, '2026-09-08', NULL, '116,405', '116', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167008, 1, 1000, 'MR202608280001', 520165001, 2, 520000101, 0.0000, 8510.0000, 0, 0.0000, NULL, NULL, NULL, '116,201,101', '116,201', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167009, 1, 1000, 'MR202608280001', 520165001, 2, 520000102, 0.0000, 3990.0000, 0, 0.0000, NULL, NULL, NULL, '116,201,102', '116,201', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167010, 1, 1000, 'MR202608280001', 520165001, 2, 520000104, 0.0000, 430.0000, 0, 0.0000, NULL, NULL, NULL, '116,202,104', '116,202', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167011, 1, 1000, 'MR202608280001', 520165001, 2, 520000111, 0.0000, 150.0000, 0, 0.0000, NULL, NULL, NULL, '116,202,111', '116,202', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167012, 1, 1000, 'MR202608280001', 520165002, 0, 520000304, 200.0000, 131.0000, 0, 69.0000, 69.0000, '2026-09-13', 'PRODUCE', '119', '119', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167013, 1, 1000, 'MR202608280001', 520165002, 1, 520000203, 69.0000, 60.0000, 0, 9.0000, 9.0000, '2026-09-13', 'PURCHASE', '119,203', '119', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167014, 1, 1000, 'MR202608280001', 520165002, 1, 520000105, 276.0000, 49080.0000, 0, 0.0000, NULL, '2026-09-13', NULL, '119,105', '119', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167015, 1, 1000, 'MR202608280001', 520165002, 1, 520000110, 138.0000, 210.0000, 0, 0.0000, NULL, '2026-09-13', NULL, '119,110', '119', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520167016, 1, 1000, 'MR202608280001', 520165002, 1, 520000404, 69.0000, 770.0000, 0, 0.0000, NULL, '2026-09-13', NULL, '119,404', '119', 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
) _v WHERE @g = 0;

-- ---- mrp_suggestion : MRP建议 (4行) ----
SET @g := (SELECT COUNT(*) FROM `mrp_suggestion` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `mrp_suggestion` (`id`, `org_id`, `tenant_id`, `batch_no`, `plan_type`, `material_id`, `qty`, `suggest_date`, `level`, `status`, `ref_type`, `ref_id`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `batch_no`, `plan_type`, `material_id`, `qty`, `suggest_date`, `level`, `status`, `ref_type`, `ref_id`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520168001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'MR202608280001' AS `batch_no`, 'PRODUCE' AS `plan_type`, 520000301 AS `material_id`, 26.0000 AS `qty`, '2026-09-08' AS `suggest_date`, 0 AS `level`, 'CONVERTED' AS `status`, 'WORK_ORDER' AS `ref_type`, 520133011 AS `ref_id`, 520000504 AS `create_by`, '2026-08-28 17:30:00' AS `create_time`, 520000504 AS `update_by`, '2026-08-28 17:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520168002, 1, 1000, 'MR202608280001', 'PRODUCE', 520000304, 69.0000, '2026-09-13', 0, 'CONVERTED', 'WORK_ORDER', 520133012, 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520168003, 1, 1000, 'MR202608280001', 'PURCHASE', 520000203, 9.0000, '2026-09-13', 1, 'PENDING', NULL, NULL, 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
  UNION ALL SELECT 520168004, 1, 1000, 'MR202608280001', 'PURCHASE', 520000110, 0.0000, '2026-09-13', 1, 'PENDING', NULL, NULL, 520000504, '2026-08-28 17:30:00', 520000504, '2026-08-28 17:30:00', 0
) _v WHERE @g = 0;

-- ---- qc_inspect : 检验单 (8行) ----
SET @g := (SELECT COUNT(*) FROM `qc_inspect` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `qc_inspect` (`id`, `org_id`, `tenant_id`, `inspect_no`, `inspect_type`, `source_type`, `source_id`, `source_no`, `template_id`, `material_id`, `supplier_id`, `sample_qty`, `qualified_qty`, `unqualified_qty`, `conclusion`, `inspector_name`, `inspect_date`, `status`, `submit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `inspect_no`, `inspect_type`, `source_type`, `source_id`, `source_no`, `template_id`, `material_id`, `supplier_id`, `sample_qty`, `qualified_qty`, `unqualified_qty`, `conclusion`, `inspector_name`, `inspect_date`, `status`, `submit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520169001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'QC202604150001' AS `inspect_no`, 'IQC' AS `inspect_type`, 'pur_instock' AS `source_type`, 520110003 AS `source_id`, 'RK202604150001' AS `source_no`, 520050001 AS `template_id`, 520000109 AS `material_id`, 520000303 AS `supplier_id`, 20 AS `sample_qty`, 20 AS `qualified_qty`, 0 AS `unqualified_qty`, 1 AS `conclusion`, '齐敏' AS `inspector_name`, '2026-04-15' AS `inspect_date`, 10 AS `status`, '2026-04-15 12:00:00' AS `submit_time`, '' AS `remark`, 520000507 AS `create_by`, '2026-04-14 11:00:00' AS `create_time`, 520000507 AS `update_by`, '2026-04-15 11:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520169002, 1, 1000, 'QC202605140001', 'IQC', 'pur_instock', 520110007, 'RK202605140001', 520050001, 520000111, 520000303, 10, 8, 2, 2, '齐敏', '2026-05-14', 10, '2026-05-14 12:00:00', '让步接收:偏差不影响装配', 520000507, '2026-05-13 11:00:00', 520000507, '2026-05-14 11:00:00', 0
  UNION ALL SELECT 520169003, 1, 1000, 'QC202606140001', 'IQC', 'pur_instock', 520110013, 'RK202606140001', 520050001, 520000102, 520000301, 60, 0, 60, 3, '齐敏', '2026-06-14', 10, '2026-06-14 12:00:00', '整批不合格', 520000507, '2026-06-13 11:00:00', 520000507, '2026-06-14 11:00:00', 0
  UNION ALL SELECT 520169004, 1, 1000, 'QC202609040001', 'IQC', 'pur_instock', 520110025, 'RK', 520050001, 520000110, 520000303, 20, 20, 0, 1, '齐敏', '2026-09-04', 10, '2026-09-04 17:30:00', '', 520000507, '2026-09-03 16:30:00', 520000507, '2026-09-04 16:30:00', 0
  UNION ALL SELECT 520169005, 1, 1000, 'QC202605240001', 'FQC', 'mrp_work_order', 520133003, 'MO202605120001', 520050002, 520000301, NULL, 12, 12, 0, 1, '齐敏', '2026-05-24', 10, '2026-05-24 18:00:00', '', 520000507, '2026-05-23 17:00:00', 520000507, '2026-05-24 17:00:00', 0
  UNION ALL SELECT 520169006, 1, 1000, 'QC202606180001', 'FQC', 'mrp_work_order', 520133006, 'MO202606110001', 520050002, 520000304, NULL, 45, 43, 2, 2, '齐敏', '2026-06-18', 10, '2026-06-18 17:30:00', '让步接收:偏差不影响装配', 520000507, '2026-06-17 16:30:00', 520000507, '2026-06-18 16:30:00', 0
  UNION ALL SELECT 520169007, 1, 1000, 'QC202608220001', 'FQC', 'mrp_work_order', 520133009, 'MO202608060001', 520050002, 520000301, NULL, 14, 12, 2, 3, '齐敏', '2026-08-22', 10, '2026-08-22 18:00:00', '整批不合格', 520000507, '2026-08-21 17:00:00', 520000507, '2026-08-22 17:00:00', 0
  UNION ALL SELECT 520169008, 1, 1000, 'QC202609030001', 'FQC', 'mrp_work_order', 520133011, 'MO202608300001', 520050002, 520000301, NULL, 10, 10, 0, 1, '齐敏', '2026-09-03', 10, '2026-09-03 18:00:00', '', 520000507, '2026-09-02 17:00:00', 520000507, '2026-09-03 17:00:00', 0
) _v WHERE @g = 0;

-- ---- qc_inspect_item : 检验单明细 (24行) ----
SET @g := (SELECT COUNT(*) FROM `qc_inspect_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `qc_inspect_item` (`id`, `org_id`, `tenant_id`, `inspect_id`, `item_name`, `standard_value`, `lower_limit`, `upper_limit`, `inspect_method`, `sample_rate`, `actual_value`, `line_result`, `seq`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `inspect_id`, `item_name`, `standard_value`, `lower_limit`, `upper_limit`, `inspect_method`, `sample_rate`, `actual_value`, `line_result`, `seq`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520170001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520169001 AS `inspect_id`, '外观' AS `item_name`, '无锈蚀/毛刺/划伤' AS `standard_value`, NULL AS `lower_limit`, NULL AS `upper_limit`, '目测' AS `inspect_method`, 100 AS `sample_rate`, '合格' AS `actual_value`, 1 AS `line_result`, 1 AS `seq`, NULL AS `remark`, 520000507 AS `create_by`, '2026-04-15 11:00:00' AS `create_time`, 520000507 AS `update_by`, '2026-04-15 11:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520170002, 1, 1000, 520169001, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '合格', 1, 2, NULL, 520000507, '2026-04-15 11:00:00', 520000507, '2026-04-15 11:00:00', 0
  UNION ALL SELECT 520170003, 1, 1000, 520169001, '硬度/性能', 'HRC28-32', 28, 32, '洛氏硬度计', 100, '合格', 1, 3, NULL, 520000507, '2026-04-15 11:00:00', 520000507, '2026-04-15 11:00:00', 0
  UNION ALL SELECT 520170004, 1, 1000, 520169002, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, '合格', 1, 1, NULL, 520000507, '2026-05-14 11:00:00', 520000507, '2026-05-14 11:00:00', 0
  UNION ALL SELECT 520170005, 1, 1000, 520169002, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '实测超差', 0, 2, NULL, 520000507, '2026-05-14 11:00:00', 520000507, '2026-05-14 11:00:00', 0
  UNION ALL SELECT 520170006, 1, 1000, 520169002, '硬度/性能', 'HRC28-32', 28, 32, '洛氏硬度计', 100, '合格', 1, 3, NULL, 520000507, '2026-05-14 11:00:00', 520000507, '2026-05-14 11:00:00', 0
  UNION ALL SELECT 520170007, 1, 1000, 520169003, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, '合格', 1, 1, NULL, 520000507, '2026-06-14 11:00:00', 520000507, '2026-06-14 11:00:00', 0
  UNION ALL SELECT 520170008, 1, 1000, 520169003, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '实测超差', 0, 2, NULL, 520000507, '2026-06-14 11:00:00', 520000507, '2026-06-14 11:00:00', 0
  UNION ALL SELECT 520170009, 1, 1000, 520169003, '硬度/性能', 'HRC28-32', 28, 32, '洛氏硬度计', 100, '合格', 1, 3, NULL, 520000507, '2026-06-14 11:00:00', 520000507, '2026-06-14 11:00:00', 0
  UNION ALL SELECT 520170010, 1, 1000, 520169004, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, '合格', 1, 1, NULL, 520000507, '2026-09-04 16:30:00', 520000507, '2026-09-04 16:30:00', 0
  UNION ALL SELECT 520170011, 1, 1000, 520169004, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '合格', 1, 2, NULL, 520000507, '2026-09-04 16:30:00', 520000507, '2026-09-04 16:30:00', 0
  UNION ALL SELECT 520170012, 1, 1000, 520169004, '硬度/性能', 'HRC28-32', 28, 32, '洛氏硬度计', 100, '合格', 1, 3, NULL, 520000507, '2026-09-04 16:30:00', 520000507, '2026-09-04 16:30:00', 0
  UNION ALL SELECT 520170013, 1, 1000, 520169005, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, '合格', 1, 1, NULL, 520000507, '2026-05-24 17:00:00', 520000507, '2026-05-24 17:00:00', 0
  UNION ALL SELECT 520170014, 1, 1000, 520169005, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '合格', 1, 2, NULL, 520000507, '2026-05-24 17:00:00', 520000507, '2026-05-24 17:00:00', 0
  UNION ALL SELECT 520170015, 1, 1000, 520169005, '硬度/性能', 'HRC28-32', 28, 32, '跑合测试', 100, '合格', 1, 3, NULL, 520000507, '2026-05-24 17:00:00', 520000507, '2026-05-24 17:00:00', 0
  UNION ALL SELECT 520170016, 1, 1000, 520169006, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, '合格', 1, 1, NULL, 520000507, '2026-06-18 16:30:00', 520000507, '2026-06-18 16:30:00', 0
  UNION ALL SELECT 520170017, 1, 1000, 520169006, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '实测超差', 0, 2, NULL, 520000507, '2026-06-18 16:30:00', 520000507, '2026-06-18 16:30:00', 0
  UNION ALL SELECT 520170018, 1, 1000, 520169006, '硬度/性能', 'HRC28-32', 28, 32, '跑合测试', 100, '合格', 1, 3, NULL, 520000507, '2026-06-18 16:30:00', 520000507, '2026-06-18 16:30:00', 0
  UNION ALL SELECT 520170019, 1, 1000, 520169007, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, '合格', 1, 1, NULL, 520000507, '2026-08-22 17:00:00', 520000507, '2026-08-22 17:00:00', 0
  UNION ALL SELECT 520170020, 1, 1000, 520169007, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '实测超差', 0, 2, NULL, 520000507, '2026-08-22 17:00:00', 520000507, '2026-08-22 17:00:00', 0
  UNION ALL SELECT 520170021, 1, 1000, 520169007, '硬度/性能', 'HRC28-32', 28, 32, '跑合测试', 100, '合格', 1, 3, NULL, 520000507, '2026-08-22 17:00:00', 520000507, '2026-08-22 17:00:00', 0
  UNION ALL SELECT 520170022, 1, 1000, 520169008, '外观', '无锈蚀/毛刺/划伤', NULL, NULL, '目测', 100, '合格', 1, 1, NULL, 520000507, '2026-09-03 17:00:00', 520000507, '2026-09-03 17:00:00', 0
  UNION ALL SELECT 520170023, 1, 1000, 520169008, '关键尺寸', '规格±0.10mm', -0.1, 0.1, '千分尺/三坐标', 100, '合格', 1, 2, NULL, 520000507, '2026-09-03 17:00:00', 520000507, '2026-09-03 17:00:00', 0
  UNION ALL SELECT 520170024, 1, 1000, 520169008, '硬度/性能', 'HRC28-32', 28, 32, '跑合测试', 100, '合格', 1, 3, NULL, 520000507, '2026-09-03 17:00:00', 520000507, '2026-09-03 17:00:00', 0
) _v WHERE @g = 0;

-- ---- qc_reject : 不合格处置单 (2行) ----
SET @g := (SELECT COUNT(*) FROM `qc_reject` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `qc_reject` (`id`, `org_id`, `tenant_id`, `reject_no`, `inspect_id`, `inspect_no`, `material_id`, `qty`, `decision`, `dispose_remark`, `approver`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `reject_no`, `inspect_id`, `inspect_no`, `material_id`, `qty`, `decision`, `dispose_remark`, `approver`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520171001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'RJ202606200001' AS `reject_no`, 520169003 AS `inspect_id`, 'QC202606140001' AS `inspect_no`, 520000102 AS `material_id`, 60 AS `qty`, 1 AS `decision`, '厚度不达标,整批退货60kg' AS `dispose_remark`, '张国民' AS `approver`, 520000507 AS `create_by`, '2026-06-20 10:00:00' AS `create_time`, 520000507 AS `update_by`, '2026-06-20 10:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520171002, 1, 1000, 'RJ202608230001', 520169007, 'QC202608220001', 520000301, 2, 4, '壳体裂纹,报废处理2台', '张国民', 520000507, '2026-08-23 09:00:00', 520000507, '2026-08-23 09:00:00', 0
) _v WHERE @g = 0;

-- ---- fin_payable : 应付单 (26行) ----
SET @g := (SELECT COUNT(*) FROM `fin_payable` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_payable` (`id`, `org_id`, `tenant_id`, `book_id`, `supplier_id`, `biz_no`, `biz_date`, `due_date`, `amount`, `settled_amount`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `supplier_id`, `biz_no`, `biz_date`, `due_date`, `amount`, `settled_amount`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520147001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 520000301 AS `supplier_id`, 'RK202604130001' AS `biz_no`, '2026-04-13' AS `biz_date`, '2026-05-13' AS `due_date`, 12105.00 AS `amount`, 12105.00 AS `settled_amount`, 'SETTLED' AS `status`, '采购入库自动生成' AS `remark`, 520000506 AS `create_by`, '2026-04-13 13:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-04-13 13:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520147002, 1, 1000, 1001, 520000301, 'RK202604240001', '2026-04-24', '2026-05-24', 8070.00, 8070.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-04-24 13:00:00', 520000506, '2026-04-24 13:00:00', 0
  UNION ALL SELECT 520147003, 1, 1000, 1001, 520000303, 'RK202604150001', '2026-04-15', '2026-05-15', 4760.00, 4760.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-04-15 13:00:00', 520000506, '2026-04-15 13:00:00', 0
  UNION ALL SELECT 520147004, 1, 1000, 1001, 520000304, 'RK202604160001', '2026-04-16', '2026-05-16', 10100.00, 10100.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-04-16 13:00:00', 520000506, '2026-04-16 13:00:00', 0
  UNION ALL SELECT 520147005, 1, 1000, 1001, 520000306, 'RK202604170001', '2026-04-17', '2026-05-17', 4680.00, 4680.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-04-17 13:00:00', 520000506, '2026-04-17 13:00:00', 0
  UNION ALL SELECT 520147006, 1, 1000, 1001, 520000302, 'RK202605130001', '2026-05-13', '2026-06-12', 9100.00, 9100.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-05-13 13:00:00', 520000506, '2026-05-13 13:00:00', 0
  UNION ALL SELECT 520147007, 1, 1000, 1001, 520000303, 'RK202605140001', '2026-05-14', '2026-06-13', 1080.00, 1080.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-05-14 13:00:00', 520000506, '2026-05-14 13:00:00', 0
  UNION ALL SELECT 520147008, 1, 1000, 1001, 520000306, 'RK202605150001', '2026-05-15', '2026-06-14', 19050.00, 19050.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-05-15 13:00:00', 520000506, '2026-05-15 13:00:00', 0
  UNION ALL SELECT 520147009, 1, 1000, 1001, 520000301, 'RK202605160001', '2026-05-16', '2026-06-15', 9100.00, 9100.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-05-16 13:00:00', 520000506, '2026-05-16 13:00:00', 0
  UNION ALL SELECT 520147010, 1, 1000, 1001, 520000304, 'RK202606110001', '2026-06-11', '2026-07-11', 5140.00, 5140.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-06-11 13:00:00', 520000506, '2026-06-11 13:00:00', 0
  UNION ALL SELECT 520147011, 1, 1000, 1001, 520000306, 'RK202606120001', '2026-06-12', '2026-07-12', 6240.00, 6240.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-06-12 13:00:00', 520000506, '2026-06-12 13:00:00', 0
  UNION ALL SELECT 520147012, 1, 1000, 1001, 520000305, 'RK202606130001', '2026-06-13', '2026-07-13', 17100.00, 17100.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-06-13 13:00:00', 520000506, '2026-06-13 13:00:00', 0
  UNION ALL SELECT 520147013, 1, 1000, 1001, 520000301, 'RK202606140001', '2026-06-14', '2026-07-14', 4350.00, 4350.00, 'SETTLED', '采购入库自动生成', 520000506, '2026-06-14 13:00:00', 520000506, '2026-06-14 13:00:00', 0
  UNION ALL SELECT 520147014, 1, 1000, 1001, 520000306, 'RK202607130001', '2026-07-13', '2026-08-12', 12700.00, 6350.00, 'PARTIAL', '采购入库自动生成', 520000506, '2026-07-13 13:00:00', 520000506, '2026-07-13 13:00:00', 0
  UNION ALL SELECT 520147015, 1, 1000, 1001, 520000303, 'RK202607140001', '2026-07-14', '2026-08-13', 3570.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-07-14 13:00:00', 520000506, '2026-07-14 13:00:00', 0
  UNION ALL SELECT 520147016, 1, 1000, 1001, 520000304, 'RK202607150001', '2026-07-15', '2026-08-14', 2500.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-07-15 13:00:00', 520000506, '2026-07-15 13:00:00', 0
  UNION ALL SELECT 520147017, 1, 1000, 1001, 520000301, 'RK202607160001', '2026-07-16', '2026-08-15', 3480.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-07-16 13:00:00', 520000506, '2026-07-16 13:00:00', 0
  UNION ALL SELECT 520147018, 1, 1000, 1001, 520000306, 'RK202608120001', '2026-08-12', '2026-09-11', 10780.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-08-12 13:00:00', 520000506, '2026-08-12 13:00:00', 0
  UNION ALL SELECT 520147019, 1, 1000, 1001, 520000306, 'RK202608260001', '2026-08-26', '2026-09-25', 4620.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-08-26 13:00:00', 520000506, '2026-08-26 13:00:00', 0
  UNION ALL SELECT 520147020, 1, 1000, 1001, 520000303, 'RK202608130001', '2026-08-13', '2026-09-12', 3320.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-08-13 13:00:00', 520000506, '2026-08-13 13:00:00', 0
  UNION ALL SELECT 520147021, 1, 1000, 1001, 520000301, 'RK202608140001', '2026-08-14', '2026-09-13', 11375.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-08-14 13:00:00', 520000506, '2026-08-14 13:00:00', 0
  UNION ALL SELECT 520147022, 1, 1000, 1001, 520000302, 'RK202608150001', '2026-08-15', '2026-09-14', 5460.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-08-15 13:00:00', 520000506, '2026-08-15 13:00:00', 0
  UNION ALL SELECT 520147023, 1, 1000, 1001, 520000306, 'RK202609030001', '2026-09-03', '2026-10-03', 9525.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-09-03 13:00:00', 520000506, '2026-09-03 13:00:00', 0
  UNION ALL SELECT 520147024, 1, 1000, 1001, 520000304, 'RK202609040001', '2026-09-04', '2026-10-04', 3400.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-09-04 13:00:00', 520000506, '2026-09-04 13:00:00', 0
  UNION ALL SELECT 520147025, 1, 1000, 1001, 520000303, 'RK202609040002', '2026-09-04', '2026-10-04', 3100.00, 0, 'UNSETTLED', '采购入库自动生成', 520000506, '2026-09-04 13:00:00', 520000506, '2026-09-04 13:00:00', 0
  UNION ALL SELECT 520147026, 1, 1000, 1001, 520000306, 'OS202606100001', '2026-06-25', '2026-07-25', 480.00, 480.00, 'SETTLED', '委外加工费', 520000504, '2026-06-25 10:00:00', 520000504, '2026-06-25 10:00:00', 0
) _v WHERE @g = 0;

-- ---- fin_receivable : 应收单 (30行) ----
SET @g := (SELECT COUNT(*) FROM `fin_receivable` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_receivable` (`id`, `org_id`, `tenant_id`, `book_id`, `customer_id`, `biz_no`, `biz_date`, `due_date`, `amount`, `settled_amount`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `customer_id`, `biz_no`, `biz_date`, `due_date`, `amount`, `settled_amount`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520149001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 520000201 AS `customer_id`, 'XS202604100001' AS `biz_no`, '2026-04-10' AS `biz_date`, '2026-05-10' AS `due_date`, 5900.00 AS `amount`, 5900.00 AS `settled_amount`, 'SETTLED' AS `status`, '销售出库自动生成' AS `remark`, 520000503 AS `create_by`, '2026-04-10 14:00:00' AS `create_time`, 520000503 AS `update_by`, '2026-04-10 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520149002, 1, 1000, 1001, 520000202, 'XS202604120001', '2026-04-12', '2026-05-12', 5300.00, 5300.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-04-12 14:00:00', 520000503, '2026-04-12 14:00:00', 0
  UNION ALL SELECT 520149003, 1, 1000, 1001, 520000203, 'XS202604150001', '2026-04-15', '2026-05-15', 3960.00, 3960.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-04-15 14:00:00', 520000503, '2026-04-15 14:00:00', 0
  UNION ALL SELECT 520149004, 1, 1000, 1001, 520000204, 'XS202604180001', '2026-04-18', '2026-05-18', 4740.00, 4740.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-04-18 14:00:00', 520000503, '2026-04-18 14:00:00', 0
  UNION ALL SELECT 520149005, 1, 1000, 1001, 520000201, 'XS202605100001', '2026-05-10', '2026-06-09', 9440.00, 9440.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-05-10 14:00:00', 520000503, '2026-05-10 14:00:00', 0
  UNION ALL SELECT 520149006, 1, 1000, 1001, 520000205, 'XS202605120001', '2026-05-12', '2026-06-11', 7950.00, 7420.00, 'PARTIAL', '销售出库自动生成', 520000503, '2026-05-12 14:00:00', 520000503, '2026-05-12 14:00:00', 0
  UNION ALL SELECT 520149007, 1, 1000, 1001, 520000208, 'XS202605140001', '2026-05-14', '2026-06-13', 4560.00, 4560.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-05-14 14:00:00', 520000503, '2026-05-14 14:00:00', 0
  UNION ALL SELECT 520149008, 1, 1000, 1001, 520000206, 'XS202605160001', '2026-05-16', '2026-06-15', 3540.00, 3540.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-05-16 14:00:00', 520000503, '2026-05-16 14:00:00', 0
  UNION ALL SELECT 520149009, 1, 1000, 1001, 520000202, 'XS202605200001', '2026-05-20', '2026-06-19', 4360.00, 4360.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-05-20 14:00:00', 520000503, '2026-05-20 14:00:00', 0
  UNION ALL SELECT 520149010, 1, 1000, 1001, 520000201, 'XS202606100001', '2026-06-10', '2026-07-10', 11800.00, 11800.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-06-10 14:00:00', 520000503, '2026-06-10 14:00:00', 0
  UNION ALL SELECT 520149011, 1, 1000, 1001, 520000204, 'XS202606120001', '2026-06-12', '2026-07-12', 9540.00, 9540.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-06-12 14:00:00', 520000503, '2026-06-12 14:00:00', 0
  UNION ALL SELECT 520149012, 1, 1000, 1001, 520000203, 'XS202606150001', '2026-06-15', '2026-07-15', 4950.00, 4950.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-06-15 14:00:00', 520000503, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520149013, 1, 1000, 1001, 520000202, 'XS202606180001', '2026-06-18', '2026-07-18', 7900.00, 7900.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-06-18 14:00:00', 520000503, '2026-06-18 14:00:00', 0
  UNION ALL SELECT 520149014, 1, 1000, 1001, 520000205, 'XS202606200001', '2026-06-20', '2026-07-20', 2340.00, 2340.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-06-20 14:00:00', 520000503, '2026-06-20 14:00:00', 0
  UNION ALL SELECT 520149015, 1, 1000, 1001, 520000201, 'XS202607100001', '2026-07-10', '2026-08-09', 4720.00, 4720.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-07-10 14:00:00', 520000503, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520149016, 1, 1000, 1001, 520000204, 'XS202607130001', '2026-07-13', '2026-08-12', 3975.00, 3975.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-07-13 14:00:00', 520000503, '2026-07-13 14:00:00', 0
  UNION ALL SELECT 520149017, 1, 1000, 1001, 520000206, 'XS202607150001', '2026-07-15', '2026-08-14', 1770.00, 1770.00, 'SETTLED', '销售出库自动生成', 520000503, '2026-07-15 14:00:00', 520000503, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520149018, 1, 1000, 1001, 520000201, 'XS202608100001', '2026-08-10', '2026-09-09', 14160.00, 10000.00, 'PARTIAL', '销售出库自动生成', 520000503, '2026-08-10 14:00:00', 520000503, '2026-08-10 14:00:00', 0
  UNION ALL SELECT 520149019, 1, 1000, 1001, 520000208, 'XS202608120001', '2026-08-12', '2026-09-11', 12640.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-08-12 14:00:00', 520000503, '2026-08-12 14:00:00', 0
  UNION ALL SELECT 520149020, 1, 1000, 1001, 520000204, 'XS202608140001', '2026-08-14', '2026-09-13', 10600.00, 6000.00, 'PARTIAL', '销售出库自动生成', 520000503, '2026-08-14 14:00:00', 520000503, '2026-08-14 14:00:00', 0
  UNION ALL SELECT 520149021, 1, 1000, 1001, 520000206, 'XS202608150001', '2026-08-15', '2026-09-14', 6840.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-08-15 14:00:00', 520000503, '2026-08-15 14:00:00', 0
  UNION ALL SELECT 520149022, 1, 1000, 1001, 520000203, 'XS202608180001', '2026-08-18', '2026-09-17', 5940.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-08-18 14:00:00', 520000503, '2026-08-18 14:00:00', 0
  UNION ALL SELECT 520149023, 1, 1000, 1001, 520000205, 'XS202608200001', '2026-08-20', '2026-09-19', 3900.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-08-20 14:00:00', 520000503, '2026-08-20 14:00:00', 0
  UNION ALL SELECT 520149024, 1, 1000, 1001, 520000205, 'RT202605220001', '2026-05-22', '2026-06-21', -530.00, 0, 'UNSETTLED', '销售退货红字应收', 520000503, '2026-05-22 15:00:00', 520000503, '2026-05-22 15:00:00', 0
  UNION ALL SELECT 520149025, 1, 1000, 1001, 520000201, 'XS202609020001', '2026-09-02', '2026-10-02', 11800.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-09-02 14:00:00', 520000503, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520149026, 1, 1000, 1001, 520000202, 'XS202609020002', '2026-09-02', '2026-10-02', 9480.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-09-02 14:00:00', 520000503, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520149027, 1, 1000, 1001, 520000204, 'XS202609030001', '2026-09-03', '2026-10-03', 8480.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-09-03 14:00:00', 520000503, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520149028, 1, 1000, 1001, 520000208, 'XS202609030002', '2026-09-03', '2026-10-03', 6540.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-09-03 14:00:00', 520000503, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520149029, 1, 1000, 1001, 520000206, 'XS202609040001', '2026-09-04', '2026-10-04', 4720.00, 0, 'UNSETTLED', '销售出库自动生成', 520000503, '2026-09-04 14:00:00', 520000503, '2026-09-04 14:00:00', 0
  UNION ALL SELECT 520149030, 1, 1000, 1001, 520000201, 'RT202608220001', '2026-08-22', '2026-09-21', -1180.00, 0, 'UNSETTLED', '销售退货红字应收', 520000503, '2026-08-22 15:00:00', 520000503, '2026-08-22 15:00:00', 0
) _v WHERE @g = 0;

-- ---- fin_receipt : 收款单 (19行) ----
SET @g := (SELECT COUNT(*) FROM `fin_receipt` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_receipt` (`id`, `org_id`, `tenant_id`, `book_id`, `receipt_no`, `customer_id`, `account_id`, `biz_date`, `amount`, `verified_amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `receipt_no`, `customer_id`, `account_id`, `biz_date`, `amount`, `verified_amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520150001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 'SK202605150001' AS `receipt_no`, 520000201 AS `customer_id`, 2 AS `account_id`, '2026-05-15' AS `biz_date`, 5900.00 AS `amount`, 5900.00 AS `verified_amount`, '货款回收' AS `remark`, 520000505 AS `create_by`, '2026-05-15 10:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-05-15 10:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520150002, 1, 1000, 1001, 'SK202605160001', 520000202, 2, '2026-05-16', 5300.00, 5300.00, '货款回收', 520000505, '2026-05-16 10:00:00', 520000505, '2026-05-16 10:00:00', 0
  UNION ALL SELECT 520150003, 1, 1000, 1001, 'SK202605180001', 520000204, 2, '2026-05-18', 4740.00, 4740.00, '货款回收', 520000505, '2026-05-18 10:00:00', 520000505, '2026-05-18 10:00:00', 0
  UNION ALL SELECT 520150004, 1, 1000, 1001, 'SK202605200001', 520000203, 2, '2026-05-20', 3960.00, 3960.00, '货款回收', 520000505, '2026-05-20 10:00:00', 520000505, '2026-05-20 10:00:00', 0
  UNION ALL SELECT 520150005, 1, 1000, 1001, 'SK202606150001', 520000201, 2, '2026-06-15', 9440.00, 9440.00, '货款回收', 520000505, '2026-06-15 10:00:00', 520000505, '2026-06-15 10:00:00', 0
  UNION ALL SELECT 520150006, 1, 1000, 1001, 'SK202606160001', 520000205, 2, '2026-06-16', 7420.00, 7420.00, '货款回收', 520000505, '2026-06-16 10:00:00', 520000505, '2026-06-16 10:00:00', 0
  UNION ALL SELECT 520150007, 1, 1000, 1001, 'SK202606180001', 520000208, 2, '2026-06-18', 4560.00, 4560.00, '货款回收', 520000505, '2026-06-18 10:00:00', 520000505, '2026-06-18 10:00:00', 0
  UNION ALL SELECT 520150008, 1, 1000, 1001, 'SK202606200001', 520000206, 2, '2026-06-20', 3540.00, 3540.00, '货款回收', 520000505, '2026-06-20 10:00:00', 520000505, '2026-06-20 10:00:00', 0
  UNION ALL SELECT 520150009, 1, 1000, 1001, 'SK202606220001', 520000202, 2, '2026-06-22', 4360.00, 4360.00, '货款回收', 520000505, '2026-06-22 10:00:00', 520000505, '2026-06-22 10:00:00', 0
  UNION ALL SELECT 520150010, 1, 1000, 1001, 'SK202607150001', 520000201, 2, '2026-07-15', 11800.00, 11800.00, '货款回收', 520000505, '2026-07-15 10:00:00', 520000505, '2026-07-15 10:00:00', 0
  UNION ALL SELECT 520150011, 1, 1000, 1001, 'SK202607160001', 520000204, 2, '2026-07-16', 9540.00, 9540.00, '货款回收', 520000505, '2026-07-16 10:00:00', 520000505, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520150012, 1, 1000, 1001, 'SK202607180001', 520000203, 2, '2026-07-18', 4950.00, 4950.00, '货款回收', 520000505, '2026-07-18 10:00:00', 520000505, '2026-07-18 10:00:00', 0
  UNION ALL SELECT 520150013, 1, 1000, 1001, 'SK202607200001', 520000202, 2, '2026-07-20', 7900.00, 7900.00, '货款回收', 520000505, '2026-07-20 10:00:00', 520000505, '2026-07-20 10:00:00', 0
  UNION ALL SELECT 520150014, 1, 1000, 1001, 'SK202607220001', 520000205, 2, '2026-07-22', 2340.00, 2340.00, '货款回收', 520000505, '2026-07-22 10:00:00', 520000505, '2026-07-22 10:00:00', 0
  UNION ALL SELECT 520150015, 1, 1000, 1001, 'SK202608100001', 520000201, 2, '2026-08-10', 4720.00, 4720.00, '货款回收', 520000505, '2026-08-10 10:00:00', 520000505, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520150016, 1, 1000, 1001, 'SK202608110001', 520000204, 2, '2026-08-11', 3975.00, 3975.00, '货款回收', 520000505, '2026-08-11 10:00:00', 520000505, '2026-08-11 10:00:00', 0
  UNION ALL SELECT 520150017, 1, 1000, 1001, 'SK202608120001', 520000206, 2, '2026-08-12', 1770.00, 1770.00, '货款回收', 520000505, '2026-08-12 10:00:00', 520000505, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520150018, 1, 1000, 1001, 'SK202609020001', 520000201, 2, '2026-09-02', 10000.00, 10000.00, '货款回收', 520000505, '2026-09-02 10:00:00', 520000505, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520150019, 1, 1000, 1001, 'SK202609020002', 520000204, 2, '2026-09-02', 6000.00, 6000.00, '货款回收', 520000505, '2026-09-02 10:00:00', 520000505, '2026-09-02 10:00:00', 0
) _v WHERE @g = 0;

-- ---- fin_receipt_item : 收款核销明细 (19行) ----
SET @g := (SELECT COUNT(*) FROM `fin_receipt_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_receipt_item` (`id`, `org_id`, `tenant_id`, `book_id`, `receipt_id`, `receivable_id`, `verify_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `receipt_id`, `receivable_id`, `verify_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520151001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 520150001 AS `receipt_id`, 520149001 AS `receivable_id`, 5900.00 AS `verify_amount`, 520000505 AS `create_by`, '2026-05-15 10:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-05-15 10:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520151002, 1, 1000, 1001, 520150002, 520149002, 5300.00, 520000505, '2026-05-16 10:00:00', 520000505, '2026-05-16 10:00:00', 0
  UNION ALL SELECT 520151003, 1, 1000, 1001, 520150003, 520149004, 4740.00, 520000505, '2026-05-18 10:00:00', 520000505, '2026-05-18 10:00:00', 0
  UNION ALL SELECT 520151004, 1, 1000, 1001, 520150004, 520149003, 3960.00, 520000505, '2026-05-20 10:00:00', 520000505, '2026-05-20 10:00:00', 0
  UNION ALL SELECT 520151005, 1, 1000, 1001, 520150005, 520149005, 9440.00, 520000505, '2026-06-15 10:00:00', 520000505, '2026-06-15 10:00:00', 0
  UNION ALL SELECT 520151006, 1, 1000, 1001, 520150006, 520149006, 7420.00, 520000505, '2026-06-16 10:00:00', 520000505, '2026-06-16 10:00:00', 0
  UNION ALL SELECT 520151007, 1, 1000, 1001, 520150007, 520149007, 4560.00, 520000505, '2026-06-18 10:00:00', 520000505, '2026-06-18 10:00:00', 0
  UNION ALL SELECT 520151008, 1, 1000, 1001, 520150008, 520149008, 3540.00, 520000505, '2026-06-20 10:00:00', 520000505, '2026-06-20 10:00:00', 0
  UNION ALL SELECT 520151009, 1, 1000, 1001, 520150009, 520149009, 4360.00, 520000505, '2026-06-22 10:00:00', 520000505, '2026-06-22 10:00:00', 0
  UNION ALL SELECT 520151010, 1, 1000, 1001, 520150010, 520149010, 11800.00, 520000505, '2026-07-15 10:00:00', 520000505, '2026-07-15 10:00:00', 0
  UNION ALL SELECT 520151011, 1, 1000, 1001, 520150011, 520149011, 9540.00, 520000505, '2026-07-16 10:00:00', 520000505, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520151012, 1, 1000, 1001, 520150012, 520149012, 4950.00, 520000505, '2026-07-18 10:00:00', 520000505, '2026-07-18 10:00:00', 0
  UNION ALL SELECT 520151013, 1, 1000, 1001, 520150013, 520149013, 7900.00, 520000505, '2026-07-20 10:00:00', 520000505, '2026-07-20 10:00:00', 0
  UNION ALL SELECT 520151014, 1, 1000, 1001, 520150014, 520149014, 2340.00, 520000505, '2026-07-22 10:00:00', 520000505, '2026-07-22 10:00:00', 0
  UNION ALL SELECT 520151015, 1, 1000, 1001, 520150015, 520149015, 4720.00, 520000505, '2026-08-10 10:00:00', 520000505, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520151016, 1, 1000, 1001, 520150016, 520149016, 3975.00, 520000505, '2026-08-11 10:00:00', 520000505, '2026-08-11 10:00:00', 0
  UNION ALL SELECT 520151017, 1, 1000, 1001, 520150017, 520149017, 1770.00, 520000505, '2026-08-12 10:00:00', 520000505, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520151018, 1, 1000, 1001, 520150018, 520149018, 10000.00, 520000505, '2026-09-02 10:00:00', 520000505, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520151019, 1, 1000, 1001, 520150019, 520149020, 6000.00, 520000505, '2026-09-02 10:00:00', 520000505, '2026-09-02 10:00:00', 0
) _v WHERE @g = 0;

-- ---- fin_payment : 付款单 (14行) ----
SET @g := (SELECT COUNT(*) FROM `fin_payment` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_payment` (`id`, `org_id`, `tenant_id`, `book_id`, `payment_no`, `supplier_id`, `account_id`, `biz_date`, `amount`, `verified_amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `payment_no`, `supplier_id`, `account_id`, `biz_date`, `amount`, `verified_amount`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520152001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 'FK202605200001' AS `payment_no`, 520000301 AS `supplier_id`, 2 AS `account_id`, '2026-05-20' AS `biz_date`, 20175.00 AS `amount`, 20175.00 AS `verified_amount`, '供应商货款结算' AS `remark`, 520000505 AS `create_by`, '2026-05-20 14:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-05-20 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520152002, 1, 1000, 1001, 'FK202605250001', 520000303, 2, '2026-05-25', 4760.00, 4760.00, '供应商货款结算', 520000505, '2026-05-25 14:00:00', 520000505, '2026-05-25 14:00:00', 0
  UNION ALL SELECT 520152003, 1, 1000, 1001, 'FK202605280001', 520000304, 2, '2026-05-28', 10100.00, 10100.00, '供应商货款结算', 520000505, '2026-05-28 14:00:00', 520000505, '2026-05-28 14:00:00', 0
  UNION ALL SELECT 520152004, 1, 1000, 1001, 'FK202606150001', 520000306, 2, '2026-06-15', 4680.00, 4680.00, '供应商货款结算', 520000505, '2026-06-15 14:00:00', 520000505, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520152005, 1, 1000, 1001, 'FK202606250001', 520000306, 2, '2026-06-25', 19050.00, 19050.00, '供应商货款结算', 520000505, '2026-06-25 14:00:00', 520000505, '2026-06-25 14:00:00', 0
  UNION ALL SELECT 520152006, 1, 1000, 1001, 'FK202607100001', 520000301, 2, '2026-07-10', 9100.00, 9100.00, '供应商货款结算', 520000505, '2026-07-10 14:00:00', 520000505, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520152007, 1, 1000, 1001, 'FK202607110001', 520000302, 2, '2026-07-11', 9100.00, 9100.00, '供应商货款结算', 520000505, '2026-07-11 14:00:00', 520000505, '2026-07-11 14:00:00', 0
  UNION ALL SELECT 520152008, 1, 1000, 1001, 'FK202607150001', 520000303, 2, '2026-07-15', 1080.00, 1080.00, '供应商货款结算', 520000505, '2026-07-15 14:00:00', 520000505, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520152009, 1, 1000, 1001, 'FK202608050001', 520000304, 2, '2026-08-05', 5140.00, 5140.00, '供应商货款结算', 520000505, '2026-08-05 14:00:00', 520000505, '2026-08-05 14:00:00', 0
  UNION ALL SELECT 520152010, 1, 1000, 1001, 'FK202608060001', 520000305, 2, '2026-08-06', 17100.00, 17100.00, '供应商货款结算', 520000505, '2026-08-06 14:00:00', 520000505, '2026-08-06 14:00:00', 0
  UNION ALL SELECT 520152011, 1, 1000, 1001, 'FK202608070001', 520000306, 2, '2026-08-07', 6240.00, 6240.00, '供应商货款结算', 520000505, '2026-08-07 14:00:00', 520000505, '2026-08-07 14:00:00', 0
  UNION ALL SELECT 520152012, 1, 1000, 1001, 'FK202608280001', 520000301, 2, '2026-08-28', 4350.00, 4350.00, '供应商货款结算', 520000505, '2026-08-28 14:00:00', 520000505, '2026-08-28 14:00:00', 0
  UNION ALL SELECT 520152013, 1, 1000, 1001, 'FK202608290001', 520000306, 2, '2026-08-29', 6350.00, 6350.00, '供应商货款结算', 520000505, '2026-08-29 14:00:00', 520000505, '2026-08-29 14:00:00', 0
  UNION ALL SELECT 520152014, 1, 1000, 1001, 'FK202609030001', 520000306, 2, '2026-09-03', 480.00, 480.00, '供应商货款结算', 520000505, '2026-09-03 14:00:00', 520000505, '2026-09-03 14:00:00', 0
) _v WHERE @g = 0;

-- ---- fin_payment_item : 付款核销明细 (15行) ----
SET @g := (SELECT COUNT(*) FROM `fin_payment_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_payment_item` (`id`, `org_id`, `tenant_id`, `book_id`, `payment_id`, `payable_id`, `verify_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `payment_id`, `payable_id`, `verify_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520153001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 520152001 AS `payment_id`, 520147001 AS `payable_id`, 12105.00 AS `verify_amount`, 520000505 AS `create_by`, '2026-05-20 14:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-05-20 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520153002, 1, 1000, 1001, 520152001, 520147002, 8070.00, 520000505, '2026-05-20 14:00:00', 520000505, '2026-05-20 14:00:00', 0
  UNION ALL SELECT 520153003, 1, 1000, 1001, 520152002, 520147003, 4760.00, 520000505, '2026-05-25 14:00:00', 520000505, '2026-05-25 14:00:00', 0
  UNION ALL SELECT 520153004, 1, 1000, 1001, 520152003, 520147004, 10100.00, 520000505, '2026-05-28 14:00:00', 520000505, '2026-05-28 14:00:00', 0
  UNION ALL SELECT 520153005, 1, 1000, 1001, 520152004, 520147005, 4680.00, 520000505, '2026-06-15 14:00:00', 520000505, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520153006, 1, 1000, 1001, 520152005, 520147008, 19050.00, 520000505, '2026-06-25 14:00:00', 520000505, '2026-06-25 14:00:00', 0
  UNION ALL SELECT 520153007, 1, 1000, 1001, 520152006, 520147009, 9100.00, 520000505, '2026-07-10 14:00:00', 520000505, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520153008, 1, 1000, 1001, 520152007, 520147006, 9100.00, 520000505, '2026-07-11 14:00:00', 520000505, '2026-07-11 14:00:00', 0
  UNION ALL SELECT 520153009, 1, 1000, 1001, 520152008, 520147007, 1080.00, 520000505, '2026-07-15 14:00:00', 520000505, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520153010, 1, 1000, 1001, 520152009, 520147010, 5140.00, 520000505, '2026-08-05 14:00:00', 520000505, '2026-08-05 14:00:00', 0
  UNION ALL SELECT 520153011, 1, 1000, 1001, 520152010, 520147012, 17100.00, 520000505, '2026-08-06 14:00:00', 520000505, '2026-08-06 14:00:00', 0
  UNION ALL SELECT 520153012, 1, 1000, 1001, 520152011, 520147011, 6240.00, 520000505, '2026-08-07 14:00:00', 520000505, '2026-08-07 14:00:00', 0
  UNION ALL SELECT 520153013, 1, 1000, 1001, 520152012, 520147013, 4350.00, 520000505, '2026-08-28 14:00:00', 520000505, '2026-08-28 14:00:00', 0
  UNION ALL SELECT 520153014, 1, 1000, 1001, 520152013, 520147014, 6350.00, 520000505, '2026-08-29 14:00:00', 520000505, '2026-08-29 14:00:00', 0
  UNION ALL SELECT 520153015, 1, 1000, 1001, 520152014, 520147026, 480.00, 520000505, '2026-09-03 14:00:00', 520000505, '2026-09-03 14:00:00', 0
) _v WHERE @g = 0;

-- ---- fin_voucher : 记账凭证 (29行) ----
SET @g := (SELECT COUNT(*) FROM `fin_voucher` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_voucher` (`id`, `org_id`, `tenant_id`, `book_id`, `voucher_no`, `voucher_date`, `word`, `attachment_count`, `total_debit`, `total_credit`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `voucher_no`, `voucher_date`, `word`, `attachment_count`, `total_debit`, `total_credit`, `status`, `audit_by`, `audit_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520155001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 'PZ202604300001' AS `voucher_no`, '2026-04-30' AS `voucher_date`, '转' AS `word`, 1 AS `attachment_count`, 17610.62 AS `total_debit`, 17610.62 AS `total_credit`, 'POSTED' AS `status`, 520000501 AS `audit_by`, '2026-04-30 11:00:00' AS `audit_time`, '本月销售收入结转(演示)' AS `remark`, 520000505 AS `create_by`, '2026-04-30 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-30 11:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520155002, 1, 1000, 1001, 'PZ202604300002', '2026-04-30', '转', 1, 11690.00, 11690.00, 'POSTED', 520000501, '2026-04-30 11:05:00', '本月销售成本结转(演示)', 520000505, '2026-04-30 09:05:00', 520000505, '2026-04-30 11:05:00', 0
  UNION ALL SELECT 520155003, 1, 1000, 1001, 'PZ202604300003', '2026-04-30', '付', 1, 3200.00, 3200.00, 'POSTED', 520000501, '2026-04-30 11:10:00', '本月管理费用报销', 520000505, '2026-04-30 09:10:00', 520000505, '2026-04-30 11:10:00', 0
  UNION ALL SELECT 520155004, 1, 1000, 1001, 'PZ202604300004', '2026-04-30', '付', 1, 1800.00, 1800.00, 'POSTED', 520000501, '2026-04-30 11:15:00', '本月销售差旅费', 520000505, '2026-04-30 09:15:00', 520000505, '2026-04-30 11:15:00', 0
  UNION ALL SELECT 520155005, 1, 1000, 1001, 'PZ202605310001', '2026-05-31', '转', 1, 26415.93, 26415.93, 'POSTED', 520000501, '2026-05-31 11:00:00', '本月销售收入结转(演示)', 520000505, '2026-05-31 09:00:00', 520000505, '2026-05-31 11:00:00', 0
  UNION ALL SELECT 520155006, 1, 1000, 1001, 'PZ202605310002', '2026-05-31', '转', 1, 17560.00, 17560.00, 'POSTED', 520000501, '2026-05-31 11:05:00', '本月销售成本结转(演示)', 520000505, '2026-05-31 09:05:00', 520000505, '2026-05-31 11:05:00', 0
  UNION ALL SELECT 520155007, 1, 1000, 1001, 'PZ202605310003', '2026-05-31', '付', 1, 2800.00, 2800.00, 'POSTED', 520000501, '2026-05-31 11:10:00', '本月管理费用报销', 520000505, '2026-05-31 09:10:00', 520000505, '2026-05-31 11:10:00', 0
  UNION ALL SELECT 520155008, 1, 1000, 1001, 'PZ202605310004', '2026-05-31', '付', 1, 2200.00, 2200.00, 'POSTED', 520000501, '2026-05-31 11:15:00', '本月销售差旅费', 520000505, '2026-05-31 09:15:00', 520000505, '2026-05-31 11:15:00', 0
  UNION ALL SELECT 520155009, 1, 1000, 1001, 'PZ202606300001', '2026-06-30', '转', 1, 32327.43, 32327.43, 'POSTED', 520000501, '2026-06-30 11:00:00', '本月销售收入结转(演示)', 520000505, '2026-06-30 09:00:00', 520000505, '2026-06-30 11:00:00', 0
  UNION ALL SELECT 520155010, 1, 1000, 1001, 'PZ202606300002', '2026-06-30', '转', 1, 21520.00, 21520.00, 'POSTED', 520000501, '2026-06-30 11:05:00', '本月销售成本结转(演示)', 520000505, '2026-06-30 09:05:00', 520000505, '2026-06-30 11:05:00', 0
  UNION ALL SELECT 520155011, 1, 1000, 1001, 'PZ202606300003', '2026-06-30', '付', 1, 3500.00, 3500.00, 'POSTED', 520000501, '2026-06-30 11:10:00', '本月管理费用报销', 520000505, '2026-06-30 09:10:00', 520000505, '2026-06-30 11:10:00', 0
  UNION ALL SELECT 520155012, 1, 1000, 1001, 'PZ202606300004', '2026-06-30', '付', 1, 2600.00, 2600.00, 'POSTED', 520000501, '2026-06-30 11:15:00', '本月销售差旅费', 520000505, '2026-06-30 09:15:00', 520000505, '2026-06-30 11:15:00', 0
  UNION ALL SELECT 520155013, 1, 1000, 1001, 'PZ202607310001', '2026-07-31', '转', 1, 9261.06, 9261.06, 'POSTED', 520000501, '2026-07-31 11:00:00', '本月销售收入结转(演示)', 520000505, '2026-07-31 09:00:00', 520000505, '2026-07-31 11:00:00', 0
  UNION ALL SELECT 520155014, 1, 1000, 1001, 'PZ202607310002', '2026-07-31', '转', 1, 6240.00, 6240.00, 'POSTED', 520000501, '2026-07-31 11:05:00', '本月销售成本结转(演示)', 520000505, '2026-07-31 09:05:00', 520000505, '2026-07-31 11:05:00', 0
  UNION ALL SELECT 520155015, 1, 1000, 1001, 'PZ202607310003', '2026-07-31', '付', 1, 2600.00, 2600.00, 'POSTED', 520000501, '2026-07-31 11:10:00', '本月管理费用报销', 520000505, '2026-07-31 09:10:00', 520000505, '2026-07-31 11:10:00', 0
  UNION ALL SELECT 520155016, 1, 1000, 1001, 'PZ202607310004', '2026-07-31', '付', 1, 1500.00, 1500.00, 'POSTED', 520000501, '2026-07-31 11:15:00', '本月销售差旅费', 520000505, '2026-07-31 09:15:00', 520000505, '2026-07-31 11:15:00', 0
  UNION ALL SELECT 520155017, 1, 1000, 1001, 'PZ202608310001', '2026-08-31', '转', 1, 47858.41, 47858.41, 'POSTED', 520000501, '2026-08-31 11:00:00', '本月销售收入结转(演示)', 520000505, '2026-08-31 09:00:00', 520000505, '2026-08-31 11:00:00', 0
  UNION ALL SELECT 520155018, 1, 1000, 1001, 'PZ202608310002', '2026-08-31', '转', 1, 31630.00, 31630.00, 'POSTED', 520000501, '2026-08-31 11:05:00', '本月销售成本结转(演示)', 520000505, '2026-08-31 09:05:00', 520000505, '2026-08-31 11:05:00', 0
  UNION ALL SELECT 520155019, 1, 1000, 1001, 'PZ202608310003', '2026-08-31', '付', 1, 3800.00, 3800.00, 'POSTED', 520000501, '2026-08-31 11:10:00', '本月管理费用报销', 520000505, '2026-08-31 09:10:00', 520000505, '2026-08-31 11:10:00', 0
  UNION ALL SELECT 520155020, 1, 1000, 1001, 'PZ202608310004', '2026-08-31', '付', 1, 3100.00, 3100.00, 'POSTED', 520000501, '2026-08-31 11:15:00', '本月销售差旅费', 520000505, '2026-08-31 09:15:00', 520000505, '2026-08-31 11:15:00', 0
  UNION ALL SELECT 520155021, 1, 1000, 1001, 'PZ202609040001', '2026-09-04', '转', 1, 36300.88, 36300.88, 'POSTED', 520000501, '2026-09-04 11:00:00', '本月销售收入结转(演示)', 520000505, '2026-09-04 09:00:00', 520000505, '2026-09-04 11:00:00', 0
  UNION ALL SELECT 520155022, 1, 1000, 1001, 'PZ202609040002', '2026-09-04', '转', 1, 24090.00, 24090.00, 'POSTED', 520000501, '2026-09-04 11:05:00', '本月销售成本结转(演示)', 520000505, '2026-09-04 09:05:00', 520000505, '2026-09-04 11:05:00', 0
  UNION ALL SELECT 520155023, 1, 1000, 1001, 'PZ202609040003', '2026-09-04', '付', 1, 2100.00, 2100.00, 'POSTED', 520000501, '2026-09-04 11:10:00', '本月管理费用报销', 520000505, '2026-09-04 09:10:00', 520000505, '2026-09-04 11:10:00', 0
  UNION ALL SELECT 520155024, 1, 1000, 1001, 'PZ202609040004', '2026-09-04', '付', 1, 1400.00, 1400.00, 'POSTED', 520000501, '2026-09-04 11:15:00', '本月销售差旅费', 520000505, '2026-09-04 09:15:00', 520000505, '2026-09-04 11:15:00', 0
  UNION ALL SELECT 520155025, 1, 1000, 1001, 'PZ202603150001', '2026-03-15', '转', 1, 480000.00, 480000.00, 'POSTED', 520000501, '2026-03-15 11:00:00', '资产入账 ZC2026-0301', 520000505, '2026-03-15 09:00:00', 520000505, '2026-03-15 11:00:00', 0
  UNION ALL SELECT 520155026, 1, 1000, 1001, 'PZ202603180001', '2026-03-18', '转', 1, 285000.00, 285000.00, 'POSTED', 520000501, '2026-03-18 11:00:00', '资产入账 ZC2026-0302', 520000505, '2026-03-18 09:00:00', 520000505, '2026-03-18 11:00:00', 0
  UNION ALL SELECT 520155027, 1, 1000, 1001, 'PZ202603200001', '2026-03-20', '转', 1, 160000.00, 160000.00, 'POSTED', 520000501, '2026-03-20 11:00:00', '资产入账 ZC2026-0303', 520000505, '2026-03-20 09:00:00', 520000505, '2026-03-20 11:00:00', 0
  UNION ALL SELECT 520155028, 1, 1000, 1001, 'PZ202604300005', '2026-04-30', '转', 1, 8717.08, 8717.08, 'POSTED', 520000501, '2026-04-30 11:20:00', '月度折旧计提 202604', 520000505, '2026-04-30 09:20:00', 520000505, '2026-04-30 11:20:00', 0
  UNION ALL SELECT 520155029, 1, 1000, 1001, 'PZ202605310005', '2026-05-31', '转', 1, 8717.08, 8717.08, 'POSTED', 520000501, '2026-05-31 11:20:00', '月度折旧计提 202605', 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 11:20:00', 0
) _v WHERE @g = 0;

-- ---- fin_voucher_entry : 凭证分录 (60行) ----
SET @g := (SELECT COUNT(*) FROM `fin_voucher_entry` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_voucher_entry` (`id`, `org_id`, `tenant_id`, `book_id`, `voucher_id`, `line_no`, `summary`, `subject_id`, `debit_amount`, `credit_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `voucher_id`, `line_no`, `summary`, `subject_id`, `debit_amount`, `credit_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520156001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 520155001 AS `voucher_id`, 1 AS `line_no`, '应收账款-当月销售结转' AS `summary`, 4 AS `subject_id`, 17610.62 AS `debit_amount`, 0.00 AS `credit_amount`, 520000505 AS `create_by`, '2026-04-30 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-30 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520156002, 1, 1000, 1001, 520155001, 2, '主营业务收入-当月销售', 27, 0.00, 17610.62, 520000505, '2026-04-30 09:00:00', 520000505, '2026-04-30 09:00:00', 0
  UNION ALL SELECT 520156003, 1, 1000, 1001, 520155002, 1, '主营业务成本-当月结转', 31, 11690.00, 0.00, 520000505, '2026-04-30 09:05:00', 520000505, '2026-04-30 09:05:00', 0
  UNION ALL SELECT 520156004, 1, 1000, 1001, 520155002, 2, '库存商品-发出成本', 9, 0.00, 11690.00, 520000505, '2026-04-30 09:05:00', 520000505, '2026-04-30 09:05:00', 0
  UNION ALL SELECT 520156005, 1, 1000, 1001, 520155003, 1, '管理费用-办公费', 34, 3200.00, 0.00, 520000505, '2026-04-30 09:10:00', 520000505, '2026-04-30 09:10:00', 0
  UNION ALL SELECT 520156006, 1, 1000, 1001, 520155003, 2, '银行存款-工行基本户', 2, 0.00, 3200.00, 520000505, '2026-04-30 09:10:00', 520000505, '2026-04-30 09:10:00', 0
  UNION ALL SELECT 520156007, 1, 1000, 1001, 520155004, 1, '销售费用-差旅费', 33, 1800.00, 0.00, 520000505, '2026-04-30 09:15:00', 520000505, '2026-04-30 09:15:00', 0
  UNION ALL SELECT 520156008, 1, 1000, 1001, 520155004, 2, '库存现金', 1, 0.00, 1800.00, 520000505, '2026-04-30 09:15:00', 520000505, '2026-04-30 09:15:00', 0
  UNION ALL SELECT 520156009, 1, 1000, 1001, 520155005, 1, '应收账款-当月销售结转', 4, 26415.93, 0.00, 520000505, '2026-05-31 09:00:00', 520000505, '2026-05-31 09:00:00', 0
  UNION ALL SELECT 520156010, 1, 1000, 1001, 520155005, 2, '主营业务收入-当月销售', 27, 0.00, 26415.93, 520000505, '2026-05-31 09:00:00', 520000505, '2026-05-31 09:00:00', 0
  UNION ALL SELECT 520156011, 1, 1000, 1001, 520155006, 1, '主营业务成本-当月结转', 31, 17560.00, 0.00, 520000505, '2026-05-31 09:05:00', 520000505, '2026-05-31 09:05:00', 0
  UNION ALL SELECT 520156012, 1, 1000, 1001, 520155006, 2, '库存商品-发出成本', 9, 0.00, 17560.00, 520000505, '2026-05-31 09:05:00', 520000505, '2026-05-31 09:05:00', 0
  UNION ALL SELECT 520156013, 1, 1000, 1001, 520155007, 1, '管理费用-办公费', 34, 2800.00, 0.00, 520000505, '2026-05-31 09:10:00', 520000505, '2026-05-31 09:10:00', 0
  UNION ALL SELECT 520156014, 1, 1000, 1001, 520155007, 2, '银行存款-工行基本户', 2, 0.00, 2800.00, 520000505, '2026-05-31 09:10:00', 520000505, '2026-05-31 09:10:00', 0
  UNION ALL SELECT 520156015, 1, 1000, 1001, 520155008, 1, '销售费用-差旅费', 33, 2200.00, 0.00, 520000505, '2026-05-31 09:15:00', 520000505, '2026-05-31 09:15:00', 0
  UNION ALL SELECT 520156016, 1, 1000, 1001, 520155008, 2, '库存现金', 1, 0.00, 2200.00, 520000505, '2026-05-31 09:15:00', 520000505, '2026-05-31 09:15:00', 0
  UNION ALL SELECT 520156017, 1, 1000, 1001, 520155009, 1, '应收账款-当月销售结转', 4, 32327.43, 0.00, 520000505, '2026-06-30 09:00:00', 520000505, '2026-06-30 09:00:00', 0
  UNION ALL SELECT 520156018, 1, 1000, 1001, 520155009, 2, '主营业务收入-当月销售', 27, 0.00, 32327.43, 520000505, '2026-06-30 09:00:00', 520000505, '2026-06-30 09:00:00', 0
  UNION ALL SELECT 520156019, 1, 1000, 1001, 520155010, 1, '主营业务成本-当月结转', 31, 21520.00, 0.00, 520000505, '2026-06-30 09:05:00', 520000505, '2026-06-30 09:05:00', 0
  UNION ALL SELECT 520156020, 1, 1000, 1001, 520155010, 2, '库存商品-发出成本', 9, 0.00, 21520.00, 520000505, '2026-06-30 09:05:00', 520000505, '2026-06-30 09:05:00', 0
  UNION ALL SELECT 520156021, 1, 1000, 1001, 520155011, 1, '管理费用-办公费', 34, 3500.00, 0.00, 520000505, '2026-06-30 09:10:00', 520000505, '2026-06-30 09:10:00', 0
  UNION ALL SELECT 520156022, 1, 1000, 1001, 520155011, 2, '银行存款-工行基本户', 2, 0.00, 3500.00, 520000505, '2026-06-30 09:10:00', 520000505, '2026-06-30 09:10:00', 0
  UNION ALL SELECT 520156023, 1, 1000, 1001, 520155012, 1, '销售费用-差旅费', 33, 2600.00, 0.00, 520000505, '2026-06-30 09:15:00', 520000505, '2026-06-30 09:15:00', 0
  UNION ALL SELECT 520156024, 1, 1000, 1001, 520155012, 2, '库存现金', 1, 0.00, 2600.00, 520000505, '2026-06-30 09:15:00', 520000505, '2026-06-30 09:15:00', 0
  UNION ALL SELECT 520156025, 1, 1000, 1001, 520155013, 1, '应收账款-当月销售结转', 4, 9261.06, 0.00, 520000505, '2026-07-31 09:00:00', 520000505, '2026-07-31 09:00:00', 0
  UNION ALL SELECT 520156026, 1, 1000, 1001, 520155013, 2, '主营业务收入-当月销售', 27, 0.00, 9261.06, 520000505, '2026-07-31 09:00:00', 520000505, '2026-07-31 09:00:00', 0
  UNION ALL SELECT 520156027, 1, 1000, 1001, 520155014, 1, '主营业务成本-当月结转', 31, 6240.00, 0.00, 520000505, '2026-07-31 09:05:00', 520000505, '2026-07-31 09:05:00', 0
  UNION ALL SELECT 520156028, 1, 1000, 1001, 520155014, 2, '库存商品-发出成本', 9, 0.00, 6240.00, 520000505, '2026-07-31 09:05:00', 520000505, '2026-07-31 09:05:00', 0
  UNION ALL SELECT 520156029, 1, 1000, 1001, 520155015, 1, '管理费用-办公费', 34, 2600.00, 0.00, 520000505, '2026-07-31 09:10:00', 520000505, '2026-07-31 09:10:00', 0
  UNION ALL SELECT 520156030, 1, 1000, 1001, 520155015, 2, '银行存款-工行基本户', 2, 0.00, 2600.00, 520000505, '2026-07-31 09:10:00', 520000505, '2026-07-31 09:10:00', 0
  UNION ALL SELECT 520156031, 1, 1000, 1001, 520155016, 1, '销售费用-差旅费', 33, 1500.00, 0.00, 520000505, '2026-07-31 09:15:00', 520000505, '2026-07-31 09:15:00', 0
  UNION ALL SELECT 520156032, 1, 1000, 1001, 520155016, 2, '库存现金', 1, 0.00, 1500.00, 520000505, '2026-07-31 09:15:00', 520000505, '2026-07-31 09:15:00', 0
  UNION ALL SELECT 520156033, 1, 1000, 1001, 520155017, 1, '应收账款-当月销售结转', 4, 47858.41, 0.00, 520000505, '2026-08-31 09:00:00', 520000505, '2026-08-31 09:00:00', 0
  UNION ALL SELECT 520156034, 1, 1000, 1001, 520155017, 2, '主营业务收入-当月销售', 27, 0.00, 47858.41, 520000505, '2026-08-31 09:00:00', 520000505, '2026-08-31 09:00:00', 0
  UNION ALL SELECT 520156035, 1, 1000, 1001, 520155018, 1, '主营业务成本-当月结转', 31, 31630.00, 0.00, 520000505, '2026-08-31 09:05:00', 520000505, '2026-08-31 09:05:00', 0
  UNION ALL SELECT 520156036, 1, 1000, 1001, 520155018, 2, '库存商品-发出成本', 9, 0.00, 31630.00, 520000505, '2026-08-31 09:05:00', 520000505, '2026-08-31 09:05:00', 0
  UNION ALL SELECT 520156037, 1, 1000, 1001, 520155019, 1, '管理费用-办公费', 34, 3800.00, 0.00, 520000505, '2026-08-31 09:10:00', 520000505, '2026-08-31 09:10:00', 0
  UNION ALL SELECT 520156038, 1, 1000, 1001, 520155019, 2, '银行存款-工行基本户', 2, 0.00, 3800.00, 520000505, '2026-08-31 09:10:00', 520000505, '2026-08-31 09:10:00', 0
  UNION ALL SELECT 520156039, 1, 1000, 1001, 520155020, 1, '销售费用-差旅费', 33, 3100.00, 0.00, 520000505, '2026-08-31 09:15:00', 520000505, '2026-08-31 09:15:00', 0
  UNION ALL SELECT 520156040, 1, 1000, 1001, 520155020, 2, '库存现金', 1, 0.00, 3100.00, 520000505, '2026-08-31 09:15:00', 520000505, '2026-08-31 09:15:00', 0
  UNION ALL SELECT 520156041, 1, 1000, 1001, 520155021, 1, '应收账款-当月销售结转', 4, 36300.88, 0.00, 520000505, '2026-09-04 09:00:00', 520000505, '2026-09-04 09:00:00', 0
  UNION ALL SELECT 520156042, 1, 1000, 1001, 520155021, 2, '主营业务收入-当月销售', 27, 0.00, 36300.88, 520000505, '2026-09-04 09:00:00', 520000505, '2026-09-04 09:00:00', 0
  UNION ALL SELECT 520156043, 1, 1000, 1001, 520155022, 1, '主营业务成本-当月结转', 31, 24090.00, 0.00, 520000505, '2026-09-04 09:05:00', 520000505, '2026-09-04 09:05:00', 0
  UNION ALL SELECT 520156044, 1, 1000, 1001, 520155022, 2, '库存商品-发出成本', 9, 0.00, 24090.00, 520000505, '2026-09-04 09:05:00', 520000505, '2026-09-04 09:05:00', 0
  UNION ALL SELECT 520156045, 1, 1000, 1001, 520155023, 1, '管理费用-办公费', 34, 2100.00, 0.00, 520000505, '2026-09-04 09:10:00', 520000505, '2026-09-04 09:10:00', 0
  UNION ALL SELECT 520156046, 1, 1000, 1001, 520155023, 2, '银行存款-工行基本户', 2, 0.00, 2100.00, 520000505, '2026-09-04 09:10:00', 520000505, '2026-09-04 09:10:00', 0
  UNION ALL SELECT 520156047, 1, 1000, 1001, 520155024, 1, '销售费用-差旅费', 33, 1400.00, 0.00, 520000505, '2026-09-04 09:15:00', 520000505, '2026-09-04 09:15:00', 0
  UNION ALL SELECT 520156048, 1, 1000, 1001, 520155024, 2, '库存现金', 1, 0.00, 1400.00, 520000505, '2026-09-04 09:15:00', 520000505, '2026-09-04 09:15:00', 0
  UNION ALL SELECT 520156049, 1, 1000, 1001, 520155025, 1, '固定资产-购入 立式加工中心 VMC-850', 10, 480000.00, 0.00, 520000505, '2026-03-15 09:00:00', 520000505, '2026-03-15 09:00:00', 0
  UNION ALL SELECT 520156050, 1, 1000, 1001, 520155025, 2, '应付账款-资产入账', 13, 0.00, 480000.00, 520000505, '2026-03-15 09:00:00', 520000505, '2026-03-15 09:00:00', 0
  UNION ALL SELECT 520156051, 1, 1000, 1001, 520155026, 1, '固定资产-购入 数控车床 CK6150', 10, 285000.00, 0.00, 520000505, '2026-03-18 09:00:00', 520000505, '2026-03-18 09:00:00', 0
  UNION ALL SELECT 520156052, 1, 1000, 1001, 520155026, 2, '应付账款-资产入账', 13, 0.00, 285000.00, 520000505, '2026-03-18 09:00:00', 520000505, '2026-03-18 09:00:00', 0
  UNION ALL SELECT 520156053, 1, 1000, 1001, 520155027, 1, '固定资产-购入 商务车 别克GL8', 10, 160000.00, 0.00, 520000505, '2026-03-20 09:00:00', 520000505, '2026-03-20 09:00:00', 0
  UNION ALL SELECT 520156054, 1, 1000, 1001, 520155027, 2, '应付账款-资产入账', 13, 0.00, 160000.00, 520000505, '2026-03-20 09:00:00', 520000505, '2026-03-20 09:00:00', 0
  UNION ALL SELECT 520156055, 1, 1000, 1001, 520155028, 1, '制造费用-折旧(加工中心+车床)', 26, 6183.75, 0.00, 520000505, '2026-04-30 09:20:00', 520000505, '2026-04-30 09:20:00', 0
  UNION ALL SELECT 520156056, 1, 1000, 1001, 520155028, 2, '管理费用-折旧(商务车)', 34, 2533.33, 0.00, 520000505, '2026-04-30 09:20:00', 520000505, '2026-04-30 09:20:00', 0
  UNION ALL SELECT 520156057, 1, 1000, 1001, 520155028, 3, '累计折旧-本月计提', 11, 0.00, 8717.08, 520000505, '2026-04-30 09:20:00', 520000505, '2026-04-30 09:20:00', 0
  UNION ALL SELECT 520156058, 1, 1000, 1001, 520155029, 1, '制造费用-折旧(加工中心+车床)', 26, 6183.75, 0.00, 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
  UNION ALL SELECT 520156059, 1, 1000, 1001, 520155029, 2, '管理费用-折旧(商务车)', 34, 2533.33, 0.00, 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
  UNION ALL SELECT 520156060, 1, 1000, 1001, 520155029, 3, '累计折旧-本月计提', 11, 0.00, 8717.08, 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
) _v WHERE @g = 0;

-- ---- fin_account_flow : 账户流水 (33行) ----
SET @g := (SELECT COUNT(*) FROM `fin_account_flow` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `fin_account_flow` (`id`, `org_id`, `tenant_id`, `book_id`, `account_id`, `biz_date`, `biz_type`, `biz_id`, `biz_no`, `direction`, `amount`, `balance_after`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `book_id`, `account_id`, `biz_date`, `biz_type`, `biz_id`, `biz_no`, `direction`, `amount`, `balance_after`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520154001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1001 AS `book_id`, 2 AS `account_id`, '2026-05-15' AS `biz_date`, 'RECEIPT' AS `biz_type`, 520150001 AS `biz_id`, 'SK202605150001' AS `biz_no`, 'INCOME' AS `direction`, 5900.00 AS `amount`, 505900.00 AS `balance_after`, '收款入账 SK202605150001' AS `remark`, 520000505 AS `create_by`, '2026-05-15 10:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-05-15 10:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520154002, 1, 1000, 1001, 2, '2026-05-16', 'RECEIPT', 520150002, 'SK202605160001', 'INCOME', 5300.00, 511200.00, '收款入账 SK202605160001', 520000505, '2026-05-16 10:00:00', 520000505, '2026-05-16 10:00:00', 0
  UNION ALL SELECT 520154003, 1, 1000, 1001, 2, '2026-05-18', 'RECEIPT', 520150003, 'SK202605180001', 'INCOME', 4740.00, 515940.00, '收款入账 SK202605180001', 520000505, '2026-05-18 10:00:00', 520000505, '2026-05-18 10:00:00', 0
  UNION ALL SELECT 520154004, 1, 1000, 1001, 2, '2026-05-20', 'RECEIPT', 520150004, 'SK202605200001', 'INCOME', 3960.00, 519900.00, '收款入账 SK202605200001', 520000505, '2026-05-20 10:00:00', 520000505, '2026-05-20 10:00:00', 0
  UNION ALL SELECT 520154005, 1, 1000, 1001, 2, '2026-05-20', 'PAYMENT', 520152001, 'FK202605200001', 'EXPENSE', 20175.00, 499725.00, '付款出账 FK202605200001', 520000505, '2026-05-20 14:00:00', 520000505, '2026-05-20 14:00:00', 0
  UNION ALL SELECT 520154006, 1, 1000, 1001, 2, '2026-05-25', 'PAYMENT', 520152002, 'FK202605250001', 'EXPENSE', 4760.00, 494965.00, '付款出账 FK202605250001', 520000505, '2026-05-25 14:00:00', 520000505, '2026-05-25 14:00:00', 0
  UNION ALL SELECT 520154007, 1, 1000, 1001, 2, '2026-05-28', 'PAYMENT', 520152003, 'FK202605280001', 'EXPENSE', 10100.00, 484865.00, '付款出账 FK202605280001', 520000505, '2026-05-28 14:00:00', 520000505, '2026-05-28 14:00:00', 0
  UNION ALL SELECT 520154008, 1, 1000, 1001, 2, '2026-06-15', 'RECEIPT', 520150005, 'SK202606150001', 'INCOME', 9440.00, 494305.00, '收款入账 SK202606150001', 520000505, '2026-06-15 10:00:00', 520000505, '2026-06-15 10:00:00', 0
  UNION ALL SELECT 520154009, 1, 1000, 1001, 2, '2026-06-15', 'PAYMENT', 520152004, 'FK202606150001', 'EXPENSE', 4680.00, 489625.00, '付款出账 FK202606150001', 520000505, '2026-06-15 14:00:00', 520000505, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520154010, 1, 1000, 1001, 2, '2026-06-16', 'RECEIPT', 520150006, 'SK202606160001', 'INCOME', 7420.00, 497045.00, '收款入账 SK202606160001', 520000505, '2026-06-16 10:00:00', 520000505, '2026-06-16 10:00:00', 0
  UNION ALL SELECT 520154011, 1, 1000, 1001, 2, '2026-06-18', 'RECEIPT', 520150007, 'SK202606180001', 'INCOME', 4560.00, 501605.00, '收款入账 SK202606180001', 520000505, '2026-06-18 10:00:00', 520000505, '2026-06-18 10:00:00', 0
  UNION ALL SELECT 520154012, 1, 1000, 1001, 2, '2026-06-20', 'RECEIPT', 520150008, 'SK202606200001', 'INCOME', 3540.00, 505145.00, '收款入账 SK202606200001', 520000505, '2026-06-20 10:00:00', 520000505, '2026-06-20 10:00:00', 0
  UNION ALL SELECT 520154013, 1, 1000, 1001, 2, '2026-06-22', 'RECEIPT', 520150009, 'SK202606220001', 'INCOME', 4360.00, 509505.00, '收款入账 SK202606220001', 520000505, '2026-06-22 10:00:00', 520000505, '2026-06-22 10:00:00', 0
  UNION ALL SELECT 520154014, 1, 1000, 1001, 2, '2026-06-25', 'PAYMENT', 520152005, 'FK202606250001', 'EXPENSE', 19050.00, 490455.00, '付款出账 FK202606250001', 520000505, '2026-06-25 14:00:00', 520000505, '2026-06-25 14:00:00', 0
  UNION ALL SELECT 520154015, 1, 1000, 1001, 2, '2026-07-10', 'PAYMENT', 520152006, 'FK202607100001', 'EXPENSE', 9100.00, 481355.00, '付款出账 FK202607100001', 520000505, '2026-07-10 14:00:00', 520000505, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520154016, 1, 1000, 1001, 2, '2026-07-11', 'PAYMENT', 520152007, 'FK202607110001', 'EXPENSE', 9100.00, 472255.00, '付款出账 FK202607110001', 520000505, '2026-07-11 14:00:00', 520000505, '2026-07-11 14:00:00', 0
  UNION ALL SELECT 520154017, 1, 1000, 1001, 2, '2026-07-15', 'RECEIPT', 520150010, 'SK202607150001', 'INCOME', 11800.00, 484055.00, '收款入账 SK202607150001', 520000505, '2026-07-15 10:00:00', 520000505, '2026-07-15 10:00:00', 0
  UNION ALL SELECT 520154018, 1, 1000, 1001, 2, '2026-07-15', 'PAYMENT', 520152008, 'FK202607150001', 'EXPENSE', 1080.00, 482975.00, '付款出账 FK202607150001', 520000505, '2026-07-15 14:00:00', 520000505, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520154019, 1, 1000, 1001, 2, '2026-07-16', 'RECEIPT', 520150011, 'SK202607160001', 'INCOME', 9540.00, 492515.00, '收款入账 SK202607160001', 520000505, '2026-07-16 10:00:00', 520000505, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520154020, 1, 1000, 1001, 2, '2026-07-18', 'RECEIPT', 520150012, 'SK202607180001', 'INCOME', 4950.00, 497465.00, '收款入账 SK202607180001', 520000505, '2026-07-18 10:00:00', 520000505, '2026-07-18 10:00:00', 0
  UNION ALL SELECT 520154021, 1, 1000, 1001, 2, '2026-07-20', 'RECEIPT', 520150013, 'SK202607200001', 'INCOME', 7900.00, 505365.00, '收款入账 SK202607200001', 520000505, '2026-07-20 10:00:00', 520000505, '2026-07-20 10:00:00', 0
  UNION ALL SELECT 520154022, 1, 1000, 1001, 2, '2026-07-22', 'RECEIPT', 520150014, 'SK202607220001', 'INCOME', 2340.00, 507705.00, '收款入账 SK202607220001', 520000505, '2026-07-22 10:00:00', 520000505, '2026-07-22 10:00:00', 0
  UNION ALL SELECT 520154023, 1, 1000, 1001, 2, '2026-08-05', 'PAYMENT', 520152009, 'FK202608050001', 'EXPENSE', 5140.00, 502565.00, '付款出账 FK202608050001', 520000505, '2026-08-05 14:00:00', 520000505, '2026-08-05 14:00:00', 0
  UNION ALL SELECT 520154024, 1, 1000, 1001, 2, '2026-08-06', 'PAYMENT', 520152010, 'FK202608060001', 'EXPENSE', 17100.00, 485465.00, '付款出账 FK202608060001', 520000505, '2026-08-06 14:00:00', 520000505, '2026-08-06 14:00:00', 0
  UNION ALL SELECT 520154025, 1, 1000, 1001, 2, '2026-08-07', 'PAYMENT', 520152011, 'FK202608070001', 'EXPENSE', 6240.00, 479225.00, '付款出账 FK202608070001', 520000505, '2026-08-07 14:00:00', 520000505, '2026-08-07 14:00:00', 0
  UNION ALL SELECT 520154026, 1, 1000, 1001, 2, '2026-08-10', 'RECEIPT', 520150015, 'SK202608100001', 'INCOME', 4720.00, 483945.00, '收款入账 SK202608100001', 520000505, '2026-08-10 10:00:00', 520000505, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520154027, 1, 1000, 1001, 2, '2026-08-11', 'RECEIPT', 520150016, 'SK202608110001', 'INCOME', 3975.00, 487920.00, '收款入账 SK202608110001', 520000505, '2026-08-11 10:00:00', 520000505, '2026-08-11 10:00:00', 0
  UNION ALL SELECT 520154028, 1, 1000, 1001, 2, '2026-08-12', 'RECEIPT', 520150017, 'SK202608120001', 'INCOME', 1770.00, 489690.00, '收款入账 SK202608120001', 520000505, '2026-08-12 10:00:00', 520000505, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520154029, 1, 1000, 1001, 2, '2026-08-28', 'PAYMENT', 520152012, 'FK202608280001', 'EXPENSE', 4350.00, 485340.00, '付款出账 FK202608280001', 520000505, '2026-08-28 14:00:00', 520000505, '2026-08-28 14:00:00', 0
  UNION ALL SELECT 520154030, 1, 1000, 1001, 2, '2026-08-29', 'PAYMENT', 520152013, 'FK202608290001', 'EXPENSE', 6350.00, 478990.00, '付款出账 FK202608290001', 520000505, '2026-08-29 14:00:00', 520000505, '2026-08-29 14:00:00', 0
  UNION ALL SELECT 520154031, 1, 1000, 1001, 2, '2026-09-02', 'RECEIPT', 520150018, 'SK202609020001', 'INCOME', 10000.00, 488990.00, '收款入账 SK202609020001', 520000505, '2026-09-02 10:00:00', 520000505, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520154032, 1, 1000, 1001, 2, '2026-09-02', 'RECEIPT', 520150019, 'SK202609020002', 'INCOME', 6000.00, 494990.00, '收款入账 SK202609020002', 520000505, '2026-09-02 10:00:00', 520000505, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520154033, 1, 1000, 1001, 2, '2026-09-03', 'PAYMENT', 520152014, 'FK202609030001', 'EXPENSE', 480.00, 494510.00, '付款出账 FK202609030001', 520000505, '2026-09-03 14:00:00', 520000505, '2026-09-03 14:00:00', 0
) _v WHERE @g = 0;

-- ---- ast_asset : 固定资产卡片 (3行) ----
SET @g := (SELECT COUNT(*) FROM `ast_asset` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `ast_asset` (`id`, `org_id`, `tenant_id`, `asset_code`, `name`, `category_id`, `spec`, `supplier_id`, `location`, `dept_id`, `keeper`, `original_value`, `residual_value`, `expected_life_months`, `depreciated_months`, `accumulated_depreciation`, `account_date`, `status`, `entry_voucher_id`, `dispose_method`, `dispose_income`, `dispose_time`, `dispose_voucher_id`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `asset_code`, `name`, `category_id`, `spec`, `supplier_id`, `location`, `dept_id`, `keeper`, `original_value`, `residual_value`, `expected_life_months`, `depreciated_months`, `accumulated_depreciation`, `account_date`, `status`, `entry_voucher_id`, `dispose_method`, `dispose_income`, `dispose_time`, `dispose_voucher_id`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520157001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'ZC2026-0301' AS `asset_code`, '立式加工中心 VMC-850' AS `name`, 3 AS `category_id`, '工作台850×500 行程700/500/600mm' AS `spec`, 520000306 AS `supplier_id`, '生产部车间一' AS `location`, 520000406 AS `dept_id`, '周建国' AS `keeper`, 480000.00 AS `original_value`, 14400.00 AS `residual_value`, 120 AS `expected_life_months`, 2 AS `depreciated_months`, 7760.00 AS `accumulated_depreciation`, '2026-03-15' AS `account_date`, 'IN_USE' AS `status`, 520155025 AS `entry_voucher_id`, NULL AS `dispose_method`, NULL AS `dispose_income`, NULL AS `dispose_time`, NULL AS `dispose_voucher_id`, '演示资产卡片' AS `remark`, 520000505 AS `create_by`, '2026-03-15 09:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-15 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520157002, 1, 1000, 'ZC2026-0302', '数控车床 CK6150', 3, '最大回转Φ500×750mm', 520000306, '生产部车间二', 520000407, '吴海', 285000.00, 8550.00, 120, 2, 4607.50, '2026-03-18', 'IN_USE', 520155026, NULL, NULL, NULL, NULL, '演示资产卡片', 520000505, '2026-03-18 09:00:00', 520000505, '2026-03-18 09:00:00', 0
  UNION ALL SELECT 520157003, 1, 1000, 'ZC2026-0303', '商务车 别克GL8', 2, '七座 2.0T 豪华型', 520000301, '总裁办车位', 520000401, '张国民', 160000.00, 8000.00, 60, 2, 5066.66, '2026-03-20', 'IN_USE', 520155027, NULL, NULL, NULL, NULL, '演示资产卡片', 520000505, '2026-03-20 09:00:00', 520000505, '2026-03-20 09:00:00', 0
) _v WHERE @g = 0;

-- ---- ast_depreciation : 折旧期间 (2行) ----
SET @g := (SELECT COUNT(*) FROM `ast_depreciation` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `ast_depreciation` (`id`, `org_id`, `tenant_id`, `period`, `status`, `voucher_id`, `asset_count`, `total_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `period`, `status`, `voucher_id`, `asset_count`, `total_amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520158001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, '202604' AS `period`, 'CLOSED' AS `status`, 520155028 AS `voucher_id`, 3 AS `asset_count`, 8717.08 AS `total_amount`, 520000505 AS `create_by`, '2026-04-30 09:20:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-30 09:20:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520158002, 1, 1000, '202605', 'CLOSED', 520155029, 3, 8717.08, 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
) _v WHERE @g = 0;

-- ---- ast_depreciation_detail : 折旧明细 (6行) ----
SET @g := (SELECT COUNT(*) FROM `ast_depreciation_detail` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `ast_depreciation_detail` (`id`, `org_id`, `tenant_id`, `period_id`, `asset_id`, `month_amount`, `voucher_id`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `period_id`, `asset_id`, `month_amount`, `voucher_id`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520159001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 520158001 AS `period_id`, 520157001 AS `asset_id`, 3880.00 AS `month_amount`, 520155028 AS `voucher_id`, 520000505 AS `create_by`, '2026-04-30 09:20:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-30 09:20:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520159002, 1, 1000, 520158001, 520157002, 2303.75, 520155028, 520000505, '2026-04-30 09:20:00', 520000505, '2026-04-30 09:20:00', 0
  UNION ALL SELECT 520159003, 1, 1000, 520158001, 520157003, 2533.33, 520155028, 520000505, '2026-04-30 09:20:00', 520000505, '2026-04-30 09:20:00', 0
  UNION ALL SELECT 520159004, 1, 1000, 520158002, 520157001, 3880.00, 520155029, 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
  UNION ALL SELECT 520159005, 1, 1000, 520158002, 520157002, 2303.75, 520155029, 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
  UNION ALL SELECT 520159006, 1, 1000, 520158002, 520157003, 2533.33, 520155029, 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
) _v WHERE @g = 0;

-- ---- budget_plan : 预算方案 (1行) ----
SET @g := (SELECT COUNT(*) FROM `budget_plan` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `budget_plan` (`id`, `tenant_id`, `org_id`, `plan_code`, `plan_name`, `budget_year`, `status`, `total_amount`, `prepare_by`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `tenant_id`, `org_id`, `plan_code`, `plan_name`, `budget_year`, `status`, `total_amount`, `prepare_by`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520160001 AS `id`, 1 AS `tenant_id`, 1 AS `org_id`, 'YS2026-DEMO' AS `plan_code`, '苏州精工2026年度预算(演示)' AS `plan_name`, 2026 AS `budget_year`, 'RELEASED' AS `status`, 216000.00 AS `total_amount`, 520000505 AS `prepare_by`, '覆盖4-9月,费用+制造费用双线预算' AS `remark`, 520000505 AS `create_by`, '2026-03-25 10:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-01 09:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- budget_item : 预算明细 (24行) ----
SET @g := (SELECT COUNT(*) FROM `budget_item` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `budget_item` (`id`, `tenant_id`, `org_id`, `plan_id`, `dept_id`, `subject_id`, `month`, `amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `tenant_id`, `org_id`, `plan_id`, `dept_id`, `subject_id`, `month`, `amount`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520161001 AS `id`, 1 AS `tenant_id`, 1 AS `org_id`, 520160001 AS `plan_id`, 520000403 AS `dept_id`, 33 AS `subject_id`, 4 AS `month`, 5000.00 AS `amount`, 520000505 AS `create_by`, '2026-03-25 10:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-03-25 10:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520161002, 1, 1, 520160001, 520000401, 34, 4, 8000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161003, 1, 1, 520160001, 520000402, 34, 4, 3000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161004, 1, 1, 520160001, 520000404, 26, 4, 20000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161005, 1, 1, 520160001, 520000403, 33, 5, 5000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161006, 1, 1, 520160001, 520000401, 34, 5, 8000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161007, 1, 1, 520160001, 520000402, 34, 5, 3000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161008, 1, 1, 520160001, 520000404, 26, 5, 20000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161009, 1, 1, 520160001, 520000403, 33, 6, 5000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161010, 1, 1, 520160001, 520000401, 34, 6, 8000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161011, 1, 1, 520160001, 520000402, 34, 6, 3000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161012, 1, 1, 520160001, 520000404, 26, 6, 20000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161013, 1, 1, 520160001, 520000403, 33, 7, 5000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161014, 1, 1, 520160001, 520000401, 34, 7, 8000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161015, 1, 1, 520160001, 520000402, 34, 7, 3000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161016, 1, 1, 520160001, 520000404, 26, 7, 20000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161017, 1, 1, 520160001, 520000403, 33, 8, 5000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161018, 1, 1, 520160001, 520000401, 34, 8, 8000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161019, 1, 1, 520160001, 520000402, 34, 8, 3000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161020, 1, 1, 520160001, 520000404, 26, 8, 20000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161021, 1, 1, 520160001, 520000403, 33, 9, 5000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161022, 1, 1, 520160001, 520000401, 34, 9, 8000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161023, 1, 1, 520160001, 520000402, 34, 9, 3000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
  UNION ALL SELECT 520161024, 1, 1, 520160001, 520000404, 26, 9, 20000.00, 520000505, '2026-03-25 10:00:00', 520000505, '2026-03-25 10:00:00', 0
) _v WHERE @g = 0;

-- ---- budget_usage_record : 预算执行数 (14行) ----
SET @g := (SELECT COUNT(*) FROM `budget_usage_record` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `budget_usage_record` (`id`, `tenant_id`, `org_id`, `dept_id`, `subject_id`, `year`, `month`, `amount`, `source_type`, `source_id`, `source_no`, `biz_date`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `tenant_id`, `org_id`, `dept_id`, `subject_id`, `year`, `month`, `amount`, `source_type`, `source_id`, `source_no`, `biz_date`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520162001 AS `id`, 1 AS `tenant_id`, 1 AS `org_id`, 520000403 AS `dept_id`, 33 AS `subject_id`, 2026 AS `year`, 4 AS `month`, 1800.00 AS `amount`, 'MANUAL' AS `source_type`, NULL AS `source_id`, 'PZ202604300004' AS `source_no`, '2026-04-30' AS `biz_date`, '销售差旅费(费用凭证推记)' AS `remark`, 520000505 AS `create_by`, '2026-04-30 09:15:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-30 09:15:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520162002, 1, 1, 520000401, 34, 2026, 4, 3200.00, 'MANUAL', NULL, 'PZ202604300003', '2026-04-30', '办公费(费用凭证推记)', 520000505, '2026-04-30 09:10:00', 520000505, '2026-04-30 09:10:00', 0
  UNION ALL SELECT 520162003, 1, 1, 520000404, 26, 2026, 4, 6183.75, 'MANUAL', NULL, 'PZ202604300005', '2026-04-30', '折旧计入制造费用', 520000505, '2026-04-30 09:20:00', 520000505, '2026-04-30 09:20:00', 0
  UNION ALL SELECT 520162004, 1, 1, 520000403, 33, 2026, 5, 2200.00, 'MANUAL', NULL, 'PZ202605310004', '2026-05-31', '销售差旅费(费用凭证推记)', 520000505, '2026-05-31 09:15:00', 520000505, '2026-05-31 09:15:00', 0
  UNION ALL SELECT 520162005, 1, 1, 520000401, 34, 2026, 5, 2800.00, 'MANUAL', NULL, 'PZ202605310003', '2026-05-31', '办公费(费用凭证推记)', 520000505, '2026-05-31 09:10:00', 520000505, '2026-05-31 09:10:00', 0
  UNION ALL SELECT 520162006, 1, 1, 520000404, 26, 2026, 5, 6183.75, 'MANUAL', NULL, 'PZ202605310005', '2026-05-31', '折旧计入制造费用', 520000505, '2026-05-31 09:20:00', 520000505, '2026-05-31 09:20:00', 0
  UNION ALL SELECT 520162007, 1, 1, 520000403, 33, 2026, 6, 2600.00, 'MANUAL', NULL, 'PZ202606300004', '2026-06-30', '销售差旅费(费用凭证推记)', 520000505, '2026-06-30 09:15:00', 520000505, '2026-06-30 09:15:00', 0
  UNION ALL SELECT 520162008, 1, 1, 520000401, 34, 2026, 6, 3500.00, 'MANUAL', NULL, 'PZ202606300003', '2026-06-30', '办公费(费用凭证推记)', 520000505, '2026-06-30 09:10:00', 520000505, '2026-06-30 09:10:00', 0
  UNION ALL SELECT 520162009, 1, 1, 520000403, 33, 2026, 7, 1500.00, 'MANUAL', NULL, 'PZ202607310004', '2026-07-31', '销售差旅费(费用凭证推记)', 520000505, '2026-07-31 09:15:00', 520000505, '2026-07-31 09:15:00', 0
  UNION ALL SELECT 520162010, 1, 1, 520000401, 34, 2026, 7, 2600.00, 'MANUAL', NULL, 'PZ202607310003', '2026-07-31', '办公费(费用凭证推记)', 520000505, '2026-07-31 09:10:00', 520000505, '2026-07-31 09:10:00', 0
  UNION ALL SELECT 520162011, 1, 1, 520000403, 33, 2026, 8, 3100.00, 'MANUAL', NULL, 'PZ202608310004', '2026-08-31', '销售差旅费(费用凭证推记)', 520000505, '2026-08-31 09:15:00', 520000505, '2026-08-31 09:15:00', 0
  UNION ALL SELECT 520162012, 1, 1, 520000401, 34, 2026, 8, 3800.00, 'MANUAL', NULL, 'PZ202608310003', '2026-08-31', '办公费(费用凭证推记)', 520000505, '2026-08-31 09:10:00', 520000505, '2026-08-31 09:10:00', 0
  UNION ALL SELECT 520162013, 1, 1, 520000403, 33, 2026, 9, 1400.00, 'MANUAL', NULL, 'PZ202609040004', '2026-09-04', '销售差旅费(费用凭证推记)', 520000505, '2026-09-04 09:15:00', 520000505, '2026-09-04 09:15:00', 0
  UNION ALL SELECT 520162014, 1, 1, 520000401, 34, 2026, 9, 2100.00, 'MANUAL', NULL, 'PZ202609040003', '2026-09-04', '办公费(费用凭证推记)', 520000505, '2026-09-04 09:10:00', 520000505, '2026-09-04 09:10:00', 0
) _v WHERE @g = 0;

-- ---- inv_sales_invoice : 销项发票 (5行) ----
SET @g := (SELECT COUNT(*) FROM `inv_sales_invoice` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_sales_invoice` (`id`, `org_id`, `tenant_id`, `invoice_no`, `invoice_date`, `customer_id`, `biz_type`, `biz_id`, `biz_no`, `amount`, `tax_rate`, `tax_amount`, `total_amount`, `red_flag`, `source_invoice_id`, `source_invoice_no`, `status`, `issue_by`, `issue_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `invoice_no`, `invoice_date`, `customer_id`, `biz_type`, `biz_id`, `biz_no`, `amount`, `tax_rate`, `tax_amount`, `total_amount`, `red_flag`, `source_invoice_id`, `source_invoice_no`, `status`, `issue_by`, `issue_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520163001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, '24680001' AS `invoice_no`, '2026-05-13' AS `invoice_date`, 520000201 AS `customer_id`, 'SAL_OUTSTOCK' AS `biz_type`, 520126005 AS `biz_id`, 'XS202605100001' AS `biz_no`, 8353.98 AS `amount`, 13.00 AS `tax_rate`, 1086.02 AS `tax_amount`, 9440.00 AS `total_amount`, 0 AS `red_flag`, NULL AS `source_invoice_id`, NULL AS `source_invoice_no`, 10 AS `status`, 520000505 AS `issue_by`, '2026-05-13 12:00:00' AS `issue_time`, '13%增值税专用发票' AS `remark`, 520000505 AS `create_by`, '2026-05-13 11:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-05-13 11:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520163002, 1, 1000, '24680002', '2026-06-12', 520000201, 'SAL_OUTSTOCK', 520126010, 'XS202606100001', 10442.48, 13.00, 1357.52, 11800.00, 0, NULL, NULL, 10, 520000505, '2026-06-12 12:00:00', '13%增值税专用发票', 520000505, '2026-06-12 11:00:00', 520000505, '2026-06-12 11:00:00', 0
  UNION ALL SELECT 520163003, 1, 1000, '24680003', '2026-08-13', 520000201, 'SAL_OUTSTOCK', 520126018, 'XS202608100001', 12530.97, 13.00, 1629.03, 14160.00, 0, NULL, NULL, 10, 520000505, '2026-08-13 12:00:00', '13%增值税专用发票', 520000505, '2026-08-13 11:00:00', 520000505, '2026-08-13 11:00:00', 0
  UNION ALL SELECT 520163004, 1, 1000, '24680004', '2026-08-17', 520000204, 'SAL_OUTSTOCK', 520126020, 'XS202608140001', 9380.53, 13.00, 1219.47, 10600.00, 0, NULL, NULL, 10, 520000505, '2026-08-17 12:00:00', '13%增值税专用发票', 520000505, '2026-08-17 11:00:00', 520000505, '2026-08-17 11:00:00', 0
  UNION ALL SELECT 520163005, 1, 1000, '24680005', '2026-09-04', 520000201, 'SAL_OUTSTOCK', 520126024, 'XS202609020001', 10442.48, 13.00, 1357.52, 11800.00, 0, NULL, NULL, 10, 520000505, '2026-09-04 12:00:00', '13%增值税专用发票', 520000505, '2026-09-04 11:00:00', 520000505, '2026-09-04 11:00:00', 0
) _v WHERE @g = 0;

-- ---- inv_purchase_invoice : 进项发票 (4行) ----
SET @g := (SELECT COUNT(*) FROM `inv_purchase_invoice` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_purchase_invoice` (`id`, `org_id`, `tenant_id`, `invoice_no`, `invoice_date`, `supplier_id`, `biz_type`, `biz_id`, `biz_no`, `amount`, `tax_rate`, `tax_amount`, `total_amount`, `red_flag`, `source_invoice_id`, `source_invoice_no`, `status`, `auth_date`, `auth_by`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `invoice_no`, `invoice_date`, `supplier_id`, `biz_type`, `biz_id`, `biz_no`, `amount`, `tax_rate`, `tax_amount`, `total_amount`, `red_flag`, `source_invoice_id`, `source_invoice_no`, `status`, `auth_date`, `auth_by`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520164001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, '31220001' AS `invoice_no`, '2026-04-14' AS `invoice_date`, 520000301 AS `supplier_id`, 'PUR_INSTOCK' AS `biz_type`, 520110001 AS `biz_id`, 'RK202604130001' AS `biz_no`, 10712.39 AS `amount`, 13.00 AS `tax_rate`, 1392.61 AS `tax_amount`, 12105.00 AS `total_amount`, 0 AS `red_flag`, NULL AS `source_invoice_id`, NULL AS `source_invoice_no`, 10 AS `status`, '2026-04-17' AS `auth_date`, 520000505 AS `auth_by`, '进项发票已认证抵扣' AS `remark`, 520000505 AS `create_by`, '2026-04-14 11:00:00' AS `create_time`, 520000505 AS `update_by`, '2026-04-14 11:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520164002, 1, 1000, '31220002', '2026-05-16', 520000306, 'PUR_INSTOCK', 520110008, 'RK202605150001', 16858.41, 13.00, 2191.59, 19050.00, 0, NULL, NULL, 10, '2026-05-19', 520000505, '进项发票已认证抵扣', 520000505, '2026-05-16 11:00:00', 520000505, '2026-05-16 11:00:00', 0
  UNION ALL SELECT 520164003, 1, 1000, '31220003', '2026-08-15', 520000301, 'PUR_INSTOCK', 520110021, 'RK202608140001', 10066.37, 13.00, 1308.63, 11375.00, 0, NULL, NULL, 10, '2026-08-18', 520000505, '进项发票已认证抵扣', 520000505, '2026-08-15 11:00:00', 520000505, '2026-08-15 11:00:00', 0
  UNION ALL SELECT 520164004, 1, 1000, '31220004', '2026-09-04', 520000306, 'PUR_INSTOCK', 520110023, 'RK202609030001', 8429.20, 13.00, 1095.80, 9525.00, 0, NULL, NULL, 10, '2026-09-07', 520000505, '进项发票已认证抵扣', 520000505, '2026-09-04 11:00:00', 520000505, '2026-09-04 11:00:00', 0
) _v WHERE @g = 0;

-- ---- wf_definition : 审批流定义 (1行) ----
SET @g := (SELECT COUNT(*) FROM `wf_definition` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `wf_definition` (`id`, `org_id`, `tenant_id`, `flow_code`, `flow_name`, `biz_type`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `flow_code`, `flow_name`, `biz_type`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520177001 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 'FLOW-PO-DEMO' AS `flow_code`, '苏州精工采购订单审批流' AS `flow_name`, 'pur_order' AS `biz_type`, 1 AS `status`, '演示:采购订单金额>0须总经理审批' AS `remark`, 520000502 AS `create_by`, '2026-04-01 10:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-04-01 10:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- wf_node : 审批流节点 (1行) ----
SET @g := (SELECT COUNT(*) FROM `wf_node` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `wf_node` (`id`, `org_id`, `tenant_id`, `definition_id`, `node_order`, `node_name`, `approver_type`, `approver_id`, `condition_expr`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `definition_id`, `node_order`, `node_name`, `approver_type`, `approver_id`, `condition_expr`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520178001 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 520177001 AS `definition_id`, 1 AS `node_order`, '总经理审批' AS `node_name`, 'USER' AS `approver_type`, 520000501 AS `approver_id`, 'amount>0' AS `condition_expr`, 520000502 AS `create_by`, '2026-04-01 10:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-04-01 10:00:00' AS `update_time`, 0 AS `deleted`
) _v WHERE @g = 0;

-- ---- wf_instance : 审批实例(2条待办) (2行) ----
SET @g := (SELECT COUNT(*) FROM `wf_instance` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `wf_instance` (`id`, `org_id`, `tenant_id`, `definition_id`, `biz_type`, `biz_id`, `biz_no`, `status`, `start_user_id`, `vars`, `end_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `definition_id`, `biz_type`, `biz_id`, `biz_no`, `status`, `start_user_id`, `vars`, `end_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520179001 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 520177001 AS `definition_id`, 'pur_order' AS `biz_type`, 520100025 AS `biz_id`, 'PO202609030001' AS `biz_no`, 'RUNNING' AS `status`, 520000502 AS `start_user_id`, '{"amount":4680.00}' AS `vars`, NULL AS `end_time`, 520000502 AS `create_by`, '2026-09-03 09:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-09-03 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520179002, 0, 1000, 520177001, 'pur_order', 520100026, 'PO202609040001', 'RUNNING', 520000502, '{"amount":5100.00}', NULL, 520000502, '2026-09-04 09:00:00', 520000502, '2026-09-04 09:00:00', 0
) _v WHERE @g = 0;

-- ---- wf_task : 审批任务(待办) (2行) ----
SET @g := (SELECT COUNT(*) FROM `wf_task` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `wf_task` (`id`, `org_id`, `tenant_id`, `instance_id`, `node_id`, `node_order`, `node_name`, `approver_id`, `status`, `opinion`, `approve_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `instance_id`, `node_id`, `node_order`, `node_name`, `approver_id`, `status`, `opinion`, `approve_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520180001 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 520179001 AS `instance_id`, 520178001 AS `node_id`, 1 AS `node_order`, '总经理审批' AS `node_name`, 520000501 AS `approver_id`, 'PENDING' AS `status`, NULL AS `opinion`, NULL AS `approve_time`, 520000502 AS `create_by`, '2026-09-03 09:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-09-03 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520180002, 0, 1000, 520179002, 520178001, 1, '总经理审批', 520000501, 'PENDING', NULL, NULL, 520000502, '2026-09-04 09:00:00', 520000502, '2026-09-04 09:00:00', 0
) _v WHERE @g = 0;

-- ---- wf_message : 消息中心 (2行) ----
SET @g := (SELECT COUNT(*) FROM `wf_message` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `wf_message` (`id`, `org_id`, `tenant_id`, `receiver_id`, `msg_type`, `title`, `content`, `biz_path`, `read_flag`, `read_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `receiver_id`, `msg_type`, `title`, `content`, `biz_path`, `read_flag`, `read_time`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520181001 AS `id`, 0 AS `org_id`, 1000 AS `tenant_id`, 520000501 AS `receiver_id`, 'TODO' AS `msg_type`, '待办:采购订单审批 PO202609030001' AS `title`, '供应商订单待您审批,金额 4680.00 元' AS `content`, '/workflow/todo' AS `biz_path`, 0 AS `read_flag`, NULL AS `read_time`, 520000502 AS `create_by`, '2026-09-03 09:00:00' AS `create_time`, 520000502 AS `update_by`, '2026-09-03 09:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520181002, 0, 1000, 520000501, 'TODO', '待办:采购订单审批 PO202609040001', '供应商订单待您审批,金额 5100.00 元', '/workflow/todo', 0, NULL, 520000502, '2026-09-04 09:00:00', 520000502, '2026-09-04 09:00:00', 0
) _v WHERE @g = 0;

-- ---- inv_stock : 库存(期末余额) (36行) ----
SET @g := (SELECT COUNT(*) FROM `inv_stock` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_stock` (`id`, `org_id`, `tenant_id`, `warehouse_id`, `location_id`, `material_id`, `batch_no`, `first_in_at`, `qty`, `version`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `warehouse_id`, `location_id`, `material_id`, `batch_no`, `first_in_at`, `qty`, `version`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520182001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 1 AS `warehouse_id`, 0 AS `location_id`, 520000101 AS `material_id`, '' AS `batch_no`, '2026-03-28 09:30:00' AS `first_in_at`, 8510.0000 AS `qty`, 1 AS `version`, 520000506 AS `create_by`, '2026-03-28 09:30:00' AS `create_time`, 520000506 AS `update_by`, '2026-03-28 09:30:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520182002, 1, 1000, 1, 0, 520000102, '', '2026-03-28 09:30:00', 3990.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182003, 1, 1000, 1, 0, 520000103, '', '2026-03-28 09:30:00', 1070, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182004, 1, 1000, 1, 0, 520000104, '2603-T', '2026-03-28 09:30:00', 130, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182005, 1, 1000, 1, 0, 520000104, '2606-A', '2026-06-13 10:00:00', 300, 1, 520000506, '2026-06-13 10:00:00', 520000506, '2026-06-13 10:00:00', 0
  UNION ALL SELECT 520182006, 1, 1000, 1, 0, 520000105, '', '2026-03-28 09:30:00', 68960.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182007, 1, 1000, 1, 0, 520000106, '', '2026-03-28 09:30:00', 59032.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182008, 1, 1000, 1, 0, 520000107, '', '2026-03-28 09:30:00', 12500, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182009, 1, 1000, 1, 0, 520000108, '', '2026-03-28 09:30:00', 8000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182010, 1, 1000, 1, 0, 520000109, '2603-Z', '2026-03-28 09:30:00', 112.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182011, 1, 1000, 1, 0, 520000109, '2604-A', '2026-04-15 10:00:00', 200, 1, 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520182012, 1, 1000, 1, 0, 520000109, '2607-A', '2026-07-14 10:00:00', 150, 1, 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520182013, 1, 1000, 1, 0, 520000109, '2608-A', '2026-08-13 10:00:00', 400, 1, 520000506, '2026-08-13 10:00:00', 520000506, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520182014, 1, 1000, 1, 0, 520000110, '2607-A', '2026-07-14 10:00:00', 150, 1, 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520182015, 1, 1000, 1, 0, 520000110, '2609-A', '2026-09-04 10:00:00', 200, 1, 520000506, '2026-09-04 10:00:00', 520000506, '2026-09-04 10:00:00', 0
  UNION ALL SELECT 520182016, 1, 1000, 1, 0, 520000111, '2603-Z', '2026-03-28 09:30:00', 100, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182017, 1, 1000, 1, 0, 520000111, '2605-A', '2026-05-14 10:00:00', 50, 1, 520000506, '2026-05-14 10:00:00', 520000506, '2026-05-14 10:00:00', 0
  UNION ALL SELECT 520182018, 1, 1000, 1, 0, 520000112, '', '2026-02-27 14:00:00', 120, 1, 520000506, '2026-02-27 14:00:00', 520000506, '2026-02-27 14:00:00', 0
  UNION ALL SELECT 520182019, 1, 1000, 1, 0, 520000201, '', '2026-03-28 09:30:00', 51.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182020, 1, 1000, 1, 0, 520000202, '', '2026-03-28 09:30:00', 31.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182021, 1, 1000, 1, 0, 520000203, '', '2026-03-28 09:30:00', 30.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182022, 1, 1000, 1, 0, 520000301, '', '2026-03-28 09:30:00', 14, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182023, 1, 1000, 520010301, 0, 520000301, '', '2026-08-27 10:35:00', 20, 1, 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520182024, 1, 1000, 520010301, 0, 520000302, '', '2026-08-27 10:35:00', 8, 1, 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520182025, 1, 1000, 1, 0, 520000303, '', '2026-03-28 09:30:00', 5, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182026, 1, 1000, 1, 0, 520000304, '', '2026-03-28 09:30:00', 69, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182027, 1, 1000, 520010301, 0, 520000304, '', '2026-08-27 10:35:00', 50, 1, 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520182028, 1, 1000, 1, 0, 520000305, '', '2026-03-28 09:30:00', 6, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182029, 1, 1000, 1, 0, 520000306, '', '2026-03-28 09:30:00', 10, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182030, 1, 1000, 1, 0, 520000307, '', '2026-03-28 09:30:00', 8, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182031, 1, 1000, 1, 0, 520000308, '', '2026-03-28 09:30:00', 4, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182032, 1, 1000, 1, 0, 520000401, '', '2026-03-28 09:30:00', 142, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182033, 1, 1000, 1, 0, 520000402, '', '2026-03-28 09:30:00', 75, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182034, 1, 1000, 1, 0, 520000403, '', '2026-03-28 09:30:00', 736.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182035, 1, 1000, 1, 0, 520000404, '', '2026-03-28 09:30:00', 740.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520182036, 1, 1000, 1, 0, 520000405, '', '2026-03-28 09:30:00', 1867.0000, 1, 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
) _v WHERE @g = 0;

-- ---- inv_movement : 库存流水(全量) (190行) ----
SET @g := (SELECT COUNT(*) FROM `inv_movement` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `inv_movement` (`id`, `org_id`, `tenant_id`, `biz_type`, `biz_id`, `biz_no`, `warehouse_id`, `material_id`, `batch_no`, `qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `biz_type`, `biz_id`, `biz_no`, `warehouse_id`, `material_id`, `batch_no`, `qty`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520500001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'OTH_IN' AS `biz_type`, 520141001 AS `biz_id`, 'OI202602270001' AS `biz_no`, 1 AS `warehouse_id`, 520000112 AS `material_id`, '' AS `batch_no`, 120 AS `qty`, '期初入库(2026年初备库)' AS `remark`, 520000506 AS `create_by`, '2026-02-27 14:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-02-27 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520500002, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000101, '', 1200, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500003, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000102, '', 800, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500004, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000103, '', 300, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500005, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000104, '2603-T', 150, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500006, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000105, '', 20000, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500007, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000106, '', 30000, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500008, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000107, '', 5000, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500009, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000108, '', 3000, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500010, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000109, '2603-Z', 240, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500011, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000110, '2603-Z', 320, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500012, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000111, '2603-Z', 100, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500013, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000201, '', 30, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500014, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000202, '', 30, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500015, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000203, '', 50, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500016, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000301, '', 20, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500017, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000302, '', 30, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500018, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000303, '', 10, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500019, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000304, '', 40, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500020, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000305, '', 36, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500021, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000306, '', 40, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500022, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000307, '', 25, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500023, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000308, '', 12, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500024, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000401, '', 150, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500025, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000402, '', 80, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500026, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000403, '', 800, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500027, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000404, '', 1000, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500028, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 1, 520000405, '', 2000, '期初入库(2026-03月末结转)', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520500029, 1, 1000, 'SAL_OUT', 520126001, 'XS202604100001', 1, 520000301, '', -5, '销售出库 XS202604100001', 520000506, '2026-04-10 14:00:00', 520000506, '2026-04-10 14:00:00', 0
  UNION ALL SELECT 520500030, 1, 1000, 'SAL_OUT', 520126002, 'XS202604120001', 1, 520000304, '', -20, '销售出库 XS202604120001', 520000506, '2026-04-12 14:00:00', 520000506, '2026-04-12 14:00:00', 0
  UNION ALL SELECT 520500031, 1, 1000, 'PUR_IN', 520110001, 'RK202604130001', 1, 520000101, '', 1800.0000, '采购入库 RK202604130001', 520000506, '2026-04-13 10:00:00', 520000506, '2026-04-13 10:00:00', 0
  UNION ALL SELECT 520500032, 1, 1000, 'PUR_IN', 520110001, 'RK202604130001', 1, 520000102, '', 900.0000, '采购入库 RK202604130001', 520000506, '2026-04-13 10:00:00', 520000506, '2026-04-13 10:00:00', 0
  UNION ALL SELECT 520500033, 1, 1000, 'PUR_IN', 520110003, 'RK202604150001', 1, 520000109, '2604-A', 200, '采购入库 RK202604150001', 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500034, 1, 1000, 'PUR_IN', 520110003, 'RK202604150001', 1, 520000110, '2604-A', 200, '采购入库 RK202604150001', 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500035, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 1, 520000201, '', -10.0000, '生产领料 MI202604150001', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500036, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 1, 520000202, '', -10.0000, '生产领料 MI202604150001', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500037, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 1, 520000109, '2603-Z', -20.0000, '生产领料 MI202604150001', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500038, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 1, 520000106, '', -120.0000, '生产领料 MI202604150001', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500039, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 1, 520000403, '', -10.0000, '生产领料 MI202604150001', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500040, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 1, 520000405, '', -20.0000, '生产领料 MI202604150001', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520500041, 1, 1000, 'SAL_OUT', 520126003, 'XS202604150001', 1, 520000306, '', -8, '销售出库 XS202604150001', 520000506, '2026-04-15 14:00:00', 520000506, '2026-04-15 14:00:00', 0
  UNION ALL SELECT 520500042, 1, 1000, 'PUR_IN', 520110004, 'RK202604160001', 1, 520000106, '', 20000, '采购入库 RK202604160001', 520000506, '2026-04-16 10:00:00', 520000506, '2026-04-16 10:00:00', 0
  UNION ALL SELECT 520500043, 1, 1000, 'PUR_IN', 520110004, 'RK202604160001', 1, 520000105, '', 30000, '采购入库 RK202604160001', 520000506, '2026-04-16 10:00:00', 520000506, '2026-04-16 10:00:00', 0
  UNION ALL SELECT 520500044, 1, 1000, 'PUR_IN', 520110005, 'RK202604170001', 1, 520000203, '', 60, '采购入库 RK202604170001', 520000506, '2026-04-17 10:00:00', 520000506, '2026-04-17 10:00:00', 0
  UNION ALL SELECT 520500045, 1, 1000, 'SAL_OUT', 520126004, 'XS202604180001', 1, 520000302, '', -3, '销售出库 XS202604180001', 520000506, '2026-04-18 14:00:00', 520000506, '2026-04-18 14:00:00', 0
  UNION ALL SELECT 520500046, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 1, 520000203, '', -30.0000, '生产领料 MI202604200001', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520500047, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 1, 520000105, '', -120.0000, '生产领料 MI202604200001', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520500048, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 1, 520000110, '2603-Z', -60.0000, '生产领料 MI202604200001', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520500049, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 1, 520000404, '', -30.0000, '生产领料 MI202604200001', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520500050, 1, 1000, 'PUR_IN', 520110002, 'RK202604240001', 1, 520000101, '', 1200.0000, '采购入库 RK202604240001', 520000506, '2026-04-24 10:00:00', 520000506, '2026-04-24 10:00:00', 0
  UNION ALL SELECT 520500051, 1, 1000, 'PUR_IN', 520110002, 'RK202604240001', 1, 520000102, '', 600.0000, '采购入库 RK202604240001', 520000506, '2026-04-24 10:00:00', 520000506, '2026-04-24 10:00:00', 0
  UNION ALL SELECT 520500052, 1, 1000, 'PROD_IN', 520133001, 'MO202604100001', 1, 520000301, '', 10, '完工入库 MO202604100001', 520000504, '2026-04-28 16:00:00', 520000504, '2026-04-28 16:00:00', 0
  UNION ALL SELECT 520500053, 1, 1000, 'PROD_IN', 520133002, 'MO202604180001', 1, 520000304, '', 30, '完工入库 MO202604180001', 520000504, '2026-04-30 16:00:00', 520000504, '2026-04-30 16:00:00', 0
  UNION ALL SELECT 520500054, 1, 1000, 'SAL_OUT', 520126005, 'XS202605100001', 1, 520000301, '', -8, '销售出库 XS202605100001', 520000506, '2026-05-10 14:00:00', 520000506, '2026-05-10 14:00:00', 0
  UNION ALL SELECT 520500055, 1, 1000, 'SAL_OUT', 520126006, 'XS202605120001', 1, 520000304, '', -30, '销售出库 XS202605120001', 520000506, '2026-05-12 14:00:00', 520000506, '2026-05-12 14:00:00', 0
  UNION ALL SELECT 520500056, 1, 1000, 'PUR_IN', 520110006, 'RK202605130001', 1, 520000103, '', 500, '采购入库 RK202605130001', 520000506, '2026-05-13 10:00:00', 520000506, '2026-05-13 10:00:00', 0
  UNION ALL SELECT 520500057, 1, 1000, 'PUR_IN', 520110007, 'RK202605140001', 1, 520000111, '2605-A', 50, '采购入库 RK202605140001', 520000506, '2026-05-14 10:00:00', 520000506, '2026-05-14 10:00:00', 0
  UNION ALL SELECT 520500058, 1, 1000, 'SAL_OUT', 520126007, 'XS202605140001', 1, 520000305, '', -12, '销售出库 XS202605140001', 520000506, '2026-05-14 14:00:00', 520000506, '2026-05-14 14:00:00', 0
  UNION ALL SELECT 520500059, 1, 1000, 'PUR_IN', 520110008, 'RK202605150001', 1, 520000201, '', 30, '采购入库 RK202605150001', 520000506, '2026-05-15 10:00:00', 520000506, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500060, 1, 1000, 'PUR_IN', 520110008, 'RK202605150001', 1, 520000202, '', 30, '采购入库 RK202605150001', 520000506, '2026-05-15 10:00:00', 520000506, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500061, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 1, 520000201, '', -12.0000, '生产领料 MI202605150001', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500062, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 1, 520000202, '', -12.0000, '生产领料 MI202605150001', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500063, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 1, 520000109, '2603-Z', -24.0000, '生产领料 MI202605150001', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500064, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 1, 520000106, '', -144.0000, '生产领料 MI202605150001', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500065, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 1, 520000403, '', -12.0000, '生产领料 MI202605150001', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500066, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 1, 520000405, '', -24.0000, '生产领料 MI202605150001', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520500067, 1, 1000, 'PUR_IN', 520110009, 'RK202605160001', 1, 520000101, '', 2000, '采购入库 RK202605160001', 520000506, '2026-05-16 10:00:00', 520000506, '2026-05-16 10:00:00', 0
  UNION ALL SELECT 520500068, 1, 1000, 'SAL_OUT', 520126008, 'XS202605160001', 1, 520000307, '', -6, '销售出库 XS202605160001', 520000506, '2026-05-16 14:00:00', 520000506, '2026-05-16 14:00:00', 0
  UNION ALL SELECT 520500069, 1, 1000, 'SAL_OUT', 520126009, 'XS202605200001', 1, 520000303, '', -2, '销售出库 XS202605200001', 520000506, '2026-05-20 14:00:00', 520000506, '2026-05-20 14:00:00', 0
  UNION ALL SELECT 520500070, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 1, 520000203, '', -40.0000, '生产领料 MI202605210001', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520500071, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 1, 520000105, '', -160.0000, '生产领料 MI202605210001', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520500072, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 1, 520000110, '2603-Z', -80.0000, '生产领料 MI202605210001', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520500073, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 1, 520000404, '', -40.0000, '生产领料 MI202605210001', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520500074, 1, 1000, 'SAL_RET', 520130001, 'RT202605220001', 1, 520000304, '', 2, '销售退货入库 RT202605220001', 520000506, '2026-05-22 15:00:00', 520000506, '2026-05-22 15:00:00', 0
  UNION ALL SELECT 520500075, 1, 1000, 'PROD_IN', 520133003, 'MO202605120001', 1, 520000301, '', 12, '完工入库 MO202605120001', 520000504, '2026-05-24 16:00:00', 520000504, '2026-05-24 16:00:00', 0
  UNION ALL SELECT 520500076, 1, 1000, 'PROD_IN', 520133004, 'MO202605190001', 1, 520000304, '', 40, '完工入库 MO202605190001', 520000504, '2026-05-26 16:00:00', 520000504, '2026-05-26 16:00:00', 0
  UNION ALL SELECT 520500077, 1, 1000, 'OTH_OUT', 520143001, 'OO202605280001', 1, 520000101, '', -200, '车间维修领用', 520000506, '2026-05-28 11:00:00', 520000506, '2026-05-28 11:00:00', 0
  UNION ALL SELECT 520500078, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 1, 520000201, '', -12.0000, '生产领料 MI202606100001', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520500079, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 1, 520000202, '', -12.0000, '生产领料 MI202606100001', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520500080, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 1, 520000109, '2603-Z', -24.0000, '生产领料 MI202606100001', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520500081, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 1, 520000106, '', -144.0000, '生产领料 MI202606100001', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520500082, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 1, 520000403, '', -12.0000, '生产领料 MI202606100001', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520500083, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 1, 520000405, '', -24.0000, '生产领料 MI202606100001', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520500084, 1, 1000, 'SAL_OUT', 520126010, 'XS202606100001', 1, 520000301, '', -10, '销售出库 XS202606100001', 520000506, '2026-06-10 14:00:00', 520000506, '2026-06-10 14:00:00', 0
  UNION ALL SELECT 520500085, 1, 1000, 'PUR_IN', 520110010, 'RK202606110001', 1, 520000108, '', 5000, '采购入库 RK202606110001', 520000506, '2026-06-11 10:00:00', 520000506, '2026-06-11 10:00:00', 0
  UNION ALL SELECT 520500086, 1, 1000, 'PUR_IN', 520110010, 'RK202606110001', 1, 520000107, '', 8000, '采购入库 RK202606110001', 520000506, '2026-06-11 10:00:00', 520000506, '2026-06-11 10:00:00', 0
  UNION ALL SELECT 520500087, 1, 1000, 'PUR_IN', 520110011, 'RK202606120001', 1, 520000203, '', 80, '采购入库 RK202606120001', 520000506, '2026-06-12 10:00:00', 520000506, '2026-06-12 10:00:00', 0
  UNION ALL SELECT 520500088, 1, 1000, 'SAL_OUT', 520126011, 'XS202606120001', 1, 520000304, '', -36, '销售出库 XS202606120001', 520000506, '2026-06-12 14:00:00', 520000506, '2026-06-12 14:00:00', 0
  UNION ALL SELECT 520500089, 1, 1000, 'PUR_IN', 520110012, 'RK202606130001', 1, 520000104, '2606-A', 300, '采购入库 RK202606130001', 520000506, '2026-06-13 10:00:00', 520000506, '2026-06-13 10:00:00', 0
  UNION ALL SELECT 520500090, 1, 1000, 'PUR_IN', 520110013, 'RK202606140001', 1, 520000102, '', 1000, '采购入库 RK202606140001', 520000506, '2026-06-14 10:00:00', 520000506, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520500091, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 1, 520000203, '', -45.0000, '生产领料 MI202606140001', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520500092, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 1, 520000105, '', -180.0000, '生产领料 MI202606140001', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520500093, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 1, 520000110, '2603-Z', -90.0000, '生产领料 MI202606140001', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520500094, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 1, 520000404, '', -45.0000, '生产领料 MI202606140001', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520500095, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 1, 520000203, '', -40.0000, '委外发料 OS202606100001', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520500096, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 1, 520000105, '', -160.0000, '委外发料 OS202606100001', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520500097, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 1, 520000110, '2603-Z', -80.0000, '委外发料 OS202606100001', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520500098, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 1, 520000404, '', -40.0000, '委外发料 OS202606100001', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520500099, 1, 1000, 'SAL_OUT', 520126012, 'XS202606150001', 1, 520000306, '', -10, '销售出库 XS202606150001', 520000506, '2026-06-15 14:00:00', 520000506, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520500100, 1, 1000, 'SAL_OUT', 520126013, 'XS202606180001', 1, 520000302, '', -5, '销售出库 XS202606180001', 520000506, '2026-06-18 14:00:00', 520000506, '2026-06-18 14:00:00', 0
  UNION ALL SELECT 520500101, 1, 1000, 'PROD_IN', 520133006, 'MO202606110001', 1, 520000304, '', 45, '完工入库 MO202606110001', 520000504, '2026-06-18 16:00:00', 520000504, '2026-06-18 16:00:00', 0
  UNION ALL SELECT 520500102, 1, 1000, 'SAL_OUT', 520126014, 'XS202606200001', 1, 520000308, '', -3, '销售出库 XS202606200001', 520000506, '2026-06-20 14:00:00', 520000506, '2026-06-20 14:00:00', 0
  UNION ALL SELECT 520500103, 1, 1000, 'PUR_RET', 520116001, 'RT202606200001', 1, 520000102, '', -60, '采购退货 RT202606200001', 520000506, '2026-06-20 14:30:00', 520000506, '2026-06-20 14:30:00', 0
  UNION ALL SELECT 520500104, 1, 1000, 'PROD_IN', 520133005, 'MO202606080001', 1, 520000301, '', 12, '完工入库 MO202606080001', 520000504, '2026-06-20 16:00:00', 520000504, '2026-06-20 16:00:00', 0
  UNION ALL SELECT 520500105, 1, 1000, 'PROD_IN', 520139001, 'OS202606100001', 1, 520000304, '', 40, '委外完工入库 OS202606100001', 520000504, '2026-06-25 10:00:00', 520000504, '2026-06-25 10:00:00', 0
  UNION ALL SELECT 520500106, 1, 1000, 'OTH_OUT', 520143002, 'OO202606250001', 1, 520000102, '', -50, '研发样品领用', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520500107, 1, 1000, 'OTH_OUT', 520143002, 'OO202606250001', 1, 520000103, '', -30, '研发样品领用', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520500108, 1, 1000, 'OTH_OUT', 520143002, 'OO202606250001', 1, 520000104, '2603-T', -20, '研发样品领用', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520500109, 1, 1000, 'ADJUST', 520145001, 'CK202606300001', 1, 520000405, '', -5, '盘点差异调平 CK202606300001', 520000506, '2026-06-30 15:00:00', 520000506, '2026-06-30 15:00:00', 0
  UNION ALL SELECT 520500110, 1, 1000, 'ADJUST', 520145001, 'CK202606300001', 1, 520000106, '', -200, '盘点差异调平 CK202606300001', 520000506, '2026-06-30 15:00:00', 520000506, '2026-06-30 15:00:00', 0
  UNION ALL SELECT 520500111, 1, 1000, 'ADJUST', 520145001, 'CK202606300001', 1, 520000101, '', 10, '盘点差异调平 CK202606300001', 520000506, '2026-06-30 15:00:00', 520000506, '2026-06-30 15:00:00', 0
  UNION ALL SELECT 520500112, 1, 1000, 'SAL_OUT', 520126015, 'XS202607100001', 1, 520000301, '', -4, '销售出库 XS202607100001', 520000506, '2026-07-10 14:00:00', 520000506, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520500113, 1, 1000, 'PUR_IN', 520110014, 'RK202607130001', 1, 520000201, '', 20, '采购入库 RK202607130001', 520000506, '2026-07-13 10:00:00', 520000506, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500114, 1, 1000, 'PUR_IN', 520110014, 'RK202607130001', 1, 520000202, '', 20, '采购入库 RK202607130001', 520000506, '2026-07-13 10:00:00', 520000506, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500115, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 1, 520000201, '', -6.0000, '生产领料 MI202607130001', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500116, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 1, 520000202, '', -6.0000, '生产领料 MI202607130001', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500117, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 1, 520000109, '2603-Z', -12.0000, '生产领料 MI202607130001', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500118, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 1, 520000106, '', -72.0000, '生产领料 MI202607130001', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500119, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 1, 520000403, '', -6.0000, '生产领料 MI202607130001', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500120, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 1, 520000405, '', -12.0000, '生产领料 MI202607130001', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520500121, 1, 1000, 'SAL_OUT', 520126016, 'XS202607130001', 1, 520000304, '', -15, '销售出库 XS202607130001', 520000506, '2026-07-13 14:00:00', 520000506, '2026-07-13 14:00:00', 0
  UNION ALL SELECT 520500122, 1, 1000, 'PUR_IN', 520110015, 'RK202607140001', 1, 520000109, '2607-A', 150, '采购入库 RK202607140001', 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520500123, 1, 1000, 'PUR_IN', 520110015, 'RK202607140001', 1, 520000110, '2607-A', 150, '采购入库 RK202607140001', 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520500124, 1, 1000, 'PUR_IN', 520110016, 'RK202607150001', 1, 520000106, '', 10000, '采购入库 RK202607150001', 520000506, '2026-07-15 10:00:00', 520000506, '2026-07-15 10:00:00', 0
  UNION ALL SELECT 520500125, 1, 1000, 'SAL_OUT', 520126017, 'XS202607150001', 1, 520000307, '', -3, '销售出库 XS202607150001', 520000506, '2026-07-15 14:00:00', 520000506, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520500126, 1, 1000, 'PUR_IN', 520110017, 'RK202607160001', 1, 520000102, '', 800, '采购入库 RK202607160001', 520000506, '2026-07-16 10:00:00', 520000506, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520500127, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 1, 520000203, '', -20.0000, '生产领料 MI202607160001', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520500128, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 1, 520000105, '', -80.0000, '生产领料 MI202607160001', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520500129, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 1, 520000110, '2603-Z', -10.0000, '生产领料 MI202607160001', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520500130, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 1, 520000110, '2604-A', -30.0000, '生产领料 MI202607160001', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520500131, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 1, 520000404, '', -20.0000, '生产领料 MI202607160001', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520500132, 1, 1000, 'PROD_IN', 520133007, 'MO202607100001', 1, 520000301, '', 6, '完工入库 MO202607100001', 520000504, '2026-07-22 16:00:00', 520000504, '2026-07-22 16:00:00', 0
  UNION ALL SELECT 520500133, 1, 1000, 'PROD_IN', 520133008, 'MO202607140001', 1, 520000304, '', 20, '完工入库 MO202607140001', 520000504, '2026-07-24 16:00:00', 520000504, '2026-07-24 16:00:00', 0
  UNION ALL SELECT 520500134, 1, 1000, 'OTH_OUT', 520143003, 'OO202607280001', 1, 520000107, '', -500, '车间一般领用', 520000506, '2026-07-28 11:00:00', 520000506, '2026-07-28 11:00:00', 0
  UNION ALL SELECT 520500135, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 1, 520000201, '', -14.0000, '生产领料 MI202608100001', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520500136, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 1, 520000202, '', -14.0000, '生产领料 MI202608100001', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520500137, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 1, 520000109, '2603-Z', -28.0000, '生产领料 MI202608100001', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520500138, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 1, 520000106, '', -168.0000, '生产领料 MI202608100001', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520500139, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 1, 520000403, '', -14.0000, '生产领料 MI202608100001', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520500140, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 1, 520000405, '', -28.0000, '生产领料 MI202608100001', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520500141, 1, 1000, 'SAL_OUT', 520126018, 'XS202608100001', 1, 520000301, '', -12, '销售出库 XS202608100001', 520000506, '2026-08-10 14:00:00', 520000506, '2026-08-10 14:00:00', 0
  UNION ALL SELECT 520500142, 1, 1000, 'PUR_IN', 520110018, 'RK202608120001', 1, 520000203, '', 70.0000, '采购入库 RK202608120001', 520000506, '2026-08-12 10:00:00', 520000506, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520500143, 1, 1000, 'PUR_IN', 520110018, 'RK202608120001', 1, 520000201, '', 14.0000, '采购入库 RK202608120001', 520000506, '2026-08-12 10:00:00', 520000506, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520500144, 1, 1000, 'SAL_OUT', 520126019, 'XS202608120001', 1, 520000302, '', -8, '销售出库 XS202608120001', 520000506, '2026-08-12 14:00:00', 520000506, '2026-08-12 14:00:00', 0
  UNION ALL SELECT 520500145, 1, 1000, 'PUR_IN', 520110020, 'RK202608130001', 1, 520000109, '2608-A', 400, '采购入库 RK202608130001', 520000506, '2026-08-13 10:00:00', 520000506, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520500146, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 1, 520000203, '', -55.0000, '生产领料 MI202608130001', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520500147, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 1, 520000105, '', -220.0000, '生产领料 MI202608130001', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520500148, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 1, 520000110, '2604-A', -110.0000, '生产领料 MI202608130001', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520500149, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 1, 520000404, '', -55.0000, '生产领料 MI202608130001', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520500150, 1, 1000, 'PUR_IN', 520110021, 'RK202608140001', 1, 520000101, '', 2500, '采购入库 RK202608140001', 520000506, '2026-08-14 10:00:00', 520000506, '2026-08-14 10:00:00', 0
  UNION ALL SELECT 520500151, 1, 1000, 'SAL_OUT', 520126020, 'XS202608140001', 1, 520000304, '', -40, '销售出库 XS202608140001', 520000506, '2026-08-14 14:00:00', 520000506, '2026-08-14 14:00:00', 0
  UNION ALL SELECT 520500152, 1, 1000, 'PUR_IN', 520110022, 'RK202608150001', 1, 520000103, '', 300, '采购入库 RK202608150001', 520000506, '2026-08-15 10:00:00', 520000506, '2026-08-15 10:00:00', 0
  UNION ALL SELECT 520500153, 1, 1000, 'SAL_OUT', 520126021, 'XS202608150001', 1, 520000305, '', -18, '销售出库 XS202608150001', 520000506, '2026-08-15 14:00:00', 520000506, '2026-08-15 14:00:00', 0
  UNION ALL SELECT 520500154, 1, 1000, 'SAL_OUT', 520126022, 'XS202608180001', 1, 520000306, '', -12, '销售出库 XS202608180001', 520000506, '2026-08-18 14:00:00', 520000506, '2026-08-18 14:00:00', 0
  UNION ALL SELECT 520500155, 1, 1000, 'SAL_OUT', 520126023, 'XS202608200001', 1, 520000308, '', -5, '销售出库 XS202608200001', 520000506, '2026-08-20 14:00:00', 520000506, '2026-08-20 14:00:00', 0
  UNION ALL SELECT 520500156, 1, 1000, 'PROD_IN', 520133010, 'MO202608030001', 1, 520000304, '', 55, '完工入库 MO202608030001', 520000504, '2026-08-20 16:00:00', 520000504, '2026-08-20 16:00:00', 0
  UNION ALL SELECT 520500157, 1, 1000, 'SAL_RET', 520130002, 'RT202608220001', 1, 520000301, '', 1, '销售退货入库 RT202608220001', 520000506, '2026-08-22 15:00:00', 520000506, '2026-08-22 15:00:00', 0
  UNION ALL SELECT 520500158, 1, 1000, 'PROD_IN', 520133009, 'MO202608060001', 1, 520000301, '', 14, '完工入库 MO202608060001', 520000504, '2026-08-22 16:00:00', 520000504, '2026-08-22 16:00:00', 0
  UNION ALL SELECT 520500159, 1, 1000, 'OTH_OUT', 520143004, 'OO202608240001', 1, 520000301, '', -2, '质检报废(FQC判定)', 520000506, '2026-08-24 11:00:00', 520000506, '2026-08-24 11:00:00', 0
  UNION ALL SELECT 520500160, 1, 1000, 'OTH_OUT', 520143005, 'OO202608250001', 1, 520000401, '', -8, '发货耗用', 520000506, '2026-08-25 11:00:00', 520000506, '2026-08-25 11:00:00', 0
  UNION ALL SELECT 520500161, 1, 1000, 'OTH_OUT', 520143005, 'OO202608250001', 1, 520000402, '', -5, '发货耗用', 520000506, '2026-08-25 11:00:00', 520000506, '2026-08-25 11:00:00', 0
  UNION ALL SELECT 520500162, 1, 1000, 'PUR_IN', 520110019, 'RK202608260001', 1, 520000203, '', 30.0000, '采购入库 RK202608260001', 520000506, '2026-08-26 10:00:00', 520000506, '2026-08-26 10:00:00', 0
  UNION ALL SELECT 520500163, 1, 1000, 'PUR_IN', 520110019, 'RK202608260001', 1, 520000201, '', 6.0000, '采购入库 RK202608260001', 520000506, '2026-08-26 10:00:00', 520000506, '2026-08-26 10:00:00', 0
  UNION ALL SELECT 520500164, 1, 1000, 'MOVE', NULL, 'TR202608270001', 1, 520000301, '', -20, '仓库调拨(主仓库→成品仓) TR202608270001', 520000506, '2026-08-27 10:30:00', 520000506, '2026-08-27 10:30:00', 0
  UNION ALL SELECT 520500165, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520010301, 520000301, '', 20, '仓库调拨(主仓库→成品仓) TR202608270001(调入)', 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520500166, 1, 1000, 'MOVE', NULL, 'TR202608270001', 1, 520000302, '', -8, '仓库调拨(主仓库→成品仓) TR202608270001', 520000506, '2026-08-27 10:30:00', 520000506, '2026-08-27 10:30:00', 0
  UNION ALL SELECT 520500167, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520010301, 520000302, '', 8, '仓库调拨(主仓库→成品仓) TR202608270001(调入)', 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520500168, 1, 1000, 'MOVE', NULL, 'TR202608270001', 1, 520000304, '', -50, '仓库调拨(主仓库→成品仓) TR202608270001', 520000506, '2026-08-27 10:30:00', 520000506, '2026-08-27 10:30:00', 0
  UNION ALL SELECT 520500169, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520010301, 520000304, '', 50, '仓库调拨(主仓库→成品仓) TR202608270001(调入)', 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520500170, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 1, 520000201, '', -10.0000, '生产领料 MI202609010001', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520500171, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 1, 520000202, '', -10.0000, '生产领料 MI202609010001', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520500172, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 1, 520000109, '2603-Z', -20.0000, '生产领料 MI202609010001', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520500173, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 1, 520000106, '', -120.0000, '生产领料 MI202609010001', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520500174, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 1, 520000403, '', -10.0000, '生产领料 MI202609010001', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520500175, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 1, 520000405, '', -20.0000, '生产领料 MI202609010001', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520500176, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 1, 520000203, '', -30.0000, '生产领料 MI202609020001', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520500177, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 1, 520000105, '', -120.0000, '生产领料 MI202609020001', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520500178, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 1, 520000110, '2604-A', -60.0000, '生产领料 MI202609020001', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520500179, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 1, 520000404, '', -30.0000, '生产领料 MI202609020001', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520500180, 1, 1000, 'SAL_OUT', 520126024, 'XS202609020001', 1, 520000301, '', -10, '销售出库 XS202609020001', 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520500181, 1, 1000, 'SAL_OUT', 520126025, 'XS202609020002', 1, 520000302, '', -6, '销售出库 XS202609020002', 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520500182, 1, 1000, 'PUR_IN', 520110023, 'RK202609030001', 1, 520000201, '', 15, '采购入库 RK202609030001', 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-03 10:00:00', 0
  UNION ALL SELECT 520500183, 1, 1000, 'PUR_IN', 520110023, 'RK202609030001', 1, 520000202, '', 15, '采购入库 RK202609030001', 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-03 10:00:00', 0
  UNION ALL SELECT 520500184, 1, 1000, 'SAL_OUT', 520126026, 'XS202609030001', 1, 520000304, '', -32, '销售出库 XS202609030001', 520000506, '2026-09-03 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520500185, 1, 1000, 'SAL_OUT', 520126027, 'XS202609030002', 1, 520000303, '', -3, '销售出库 XS202609030002', 520000506, '2026-09-03 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520500186, 1, 1000, 'PROD_IN', 520133011, 'MO202608300001', 1, 520000301, '', 10, '完工入库 MO202608300001', 520000504, '2026-09-03 16:00:00', 520000504, '2026-09-03 16:00:00', 0
  UNION ALL SELECT 520500187, 1, 1000, 'PUR_IN', 520110024, 'RK202609040001', 1, 520000105, '', 20000, '采购入库 RK202609040001', 520000506, '2026-09-04 10:00:00', 520000506, '2026-09-04 10:00:00', 0
  UNION ALL SELECT 520500188, 1, 1000, 'PUR_IN', 520110025, 'RK202609040002', 1, 520000110, '2609-A', 200, '采购入库 RK202609040002', 520000506, '2026-09-04 10:00:00', 520000506, '2026-09-04 10:00:00', 0
  UNION ALL SELECT 520500189, 1, 1000, 'SAL_OUT', 520126028, 'XS202609040001', 1, 520000307, '', -8, '销售出库 XS202609040001', 520000506, '2026-09-04 14:00:00', 520000506, '2026-09-04 14:00:00', 0
  UNION ALL SELECT 520500190, 1, 1000, 'PROD_IN', 520133012, 'MO202609010001', 1, 520000304, '', 20, '完工入库 MO202609010001', 520000504, '2026-09-04 16:00:00', 520000504, '2026-09-04 16:00:00', 0
) _v WHERE @g = 0;

-- ---- cost_flow : 成本流水(余额连续) (190行) ----
SET @g := (SELECT COUNT(*) FROM `cost_flow` WHERE `id` BETWEEN 520000000 AND 529999999);
INSERT INTO `cost_flow` (`id`, `org_id`, `tenant_id`, `biz_type`, `biz_id`, `biz_no`, `movement_id`, `material_id`, `warehouse_id`, `biz_date`, `qty`, `unit_cost`, `amount`, `balance_qty`, `balance_cost`, `period`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT `id`, `org_id`, `tenant_id`, `biz_type`, `biz_id`, `biz_no`, `movement_id`, `material_id`, `warehouse_id`, `biz_date`, `qty`, `unit_cost`, `amount`, `balance_qty`, `balance_cost`, `period`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted` FROM (
  SELECT 520600001 AS `id`, 1 AS `org_id`, 1000 AS `tenant_id`, 'OTH_IN' AS `biz_type`, 520141001 AS `biz_id`, 'OI202602270001' AS `biz_no`, 520500001 AS `movement_id`, 520000112 AS `material_id`, 1 AS `warehouse_id`, '2026-02-27' AS `biz_date`, 120 AS `qty`, 34.00 AS `unit_cost`, 4080.00 AS `amount`, 120 AS `balance_qty`, 4080.00 AS `balance_cost`, '202602' AS `period`, 520000506 AS `create_by`, '2026-02-27 14:00:00' AS `create_time`, 520000506 AS `update_by`, '2026-02-27 14:00:00' AS `update_time`, 0 AS `deleted`
  UNION ALL SELECT 520600002, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500002, 520000101, 1, '2026-03-28', 1200, 4.55, 5460.00, 1200, 5460.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600003, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500003, 520000102, 1, '2026-03-28', 800, 4.35, 3480.00, 800, 3480.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600004, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500004, 520000103, 1, '2026-03-28', 300, 18.20, 5460.00, 300, 5460.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600005, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500005, 520000104, 1, '2026-03-28', 150, 57.00, 8550.00, 150, 8550.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600006, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500006, 520000105, 1, '2026-03-28', 20000, 0.17, 3400.00, 20000, 3400.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600007, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500007, 520000106, 1, '2026-03-28', 30000, 0.25, 7500.00, 30000, 7500.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600008, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500008, 520000107, 1, '2026-03-28', 5000, 0.33, 1650.00, 5000, 1650.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600009, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500009, 520000108, 1, '2026-03-28', 3000, 0.50, 1500.00, 3000, 1500.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600010, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500010, 520000109, 1, '2026-03-28', 240, 8.30, 1992.00, 240, 1992.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600011, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500011, 520000110, 1, '2026-03-28', 320, 15.50, 4960.00, 320, 4960.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600012, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500012, 520000111, 1, '2026-03-28', 100, 21.60, 2160.00, 100, 2160.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600013, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500013, 520000201, 1, '2026-03-28', 30, 380.00, 11400.00, 30, 11400.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600014, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500014, 520000202, 1, '2026-03-28', 30, 255.00, 7650.00, 30, 7650.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600015, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500015, 520000203, 1, '2026-03-28', 50, 78.00, 3900.00, 50, 3900.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600016, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500016, 520000301, 1, '2026-03-28', 20, 750.00, 15000.00, 20, 15000.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600017, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500017, 520000302, 1, '2026-03-28', 30, 900.00, 27000.00, 30, 27000.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600018, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500018, 520000303, 1, '2026-03-28', 10, 1250.00, 12500.00, 10, 12500.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600019, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500019, 520000304, 1, '2026-03-28', 40, 150.00, 6000.00, 40, 6000.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600020, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500020, 520000305, 1, '2026-03-28', 36, 215.00, 7740.00, 36, 7740.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600021, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500021, 520000306, 1, '2026-03-28', 40, 280.00, 11200.00, 40, 11200.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600022, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500022, 520000307, 1, '2026-03-28', 25, 330.00, 8250.00, 25, 8250.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600023, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500023, 520000308, 1, '2026-03-28', 12, 440.00, 5280.00, 12, 5280.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600024, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500024, 520000401, 1, '2026-03-28', 150, 95.00, 14250.00, 150, 14250.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600025, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500025, 520000402, 1, '2026-03-28', 80, 12.00, 960.00, 80, 960.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600026, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500026, 520000403, 1, '2026-03-28', 800, 6.50, 5200.00, 800, 5200.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600027, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500027, 520000404, 1, '2026-03-28', 1000, 1.20, 1200.00, 1000, 1200.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600028, 1, 1000, 'OTH_IN', 520141002, 'OI202603280001', 520500028, 520000405, 1, '2026-03-28', 2000, 0.30, 600.00, 2000, 600.00, '202603', 520000506, '2026-03-28 09:30:00', 520000506, '2026-03-28 09:30:00', 0
  UNION ALL SELECT 520600029, 1, 1000, 'SAL_OUT', 520126001, 'XS202604100001', 520500029, 520000301, 1, '2026-04-10', -5, 750.00, -3750.00, 15, 11250.00, '202604', 520000506, '2026-04-10 14:00:00', 520000506, '2026-04-10 14:00:00', 0
  UNION ALL SELECT 520600030, 1, 1000, 'SAL_OUT', 520126002, 'XS202604120001', 520500030, 520000304, 1, '2026-04-12', -20, 150.00, -3000.00, 20, 3000.00, '202604', 520000506, '2026-04-12 14:00:00', 520000506, '2026-04-12 14:00:00', 0
  UNION ALL SELECT 520600031, 1, 1000, 'PUR_IN', 520110001, 'RK202604130001', 520500031, 520000101, 1, '2026-04-13', 1800.0000, 4.55, 8190.00, 3000.0000, 13650.00, '202604', 520000506, '2026-04-13 10:00:00', 520000506, '2026-04-13 10:00:00', 0
  UNION ALL SELECT 520600032, 1, 1000, 'PUR_IN', 520110001, 'RK202604130001', 520500032, 520000102, 1, '2026-04-13', 900.0000, 4.35, 3915.00, 1700.0000, 7395.00, '202604', 520000506, '2026-04-13 10:00:00', 520000506, '2026-04-13 10:00:00', 0
  UNION ALL SELECT 520600033, 1, 1000, 'PUR_IN', 520110003, 'RK202604150001', 520500033, 520000109, 1, '2026-04-15', 200, 8.30, 1660.00, 440, 3652.00, '202604', 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600034, 1, 1000, 'PUR_IN', 520110003, 'RK202604150001', 520500034, 520000110, 1, '2026-04-15', 200, 15.50, 3100.00, 520, 8060.00, '202604', 520000506, '2026-04-15 10:00:00', 520000506, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600035, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 520500035, 520000201, 1, '2026-04-15', -10.0000, 380.00, -3800.00, 20.0000, 7600.00, '202604', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600036, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 520500036, 520000202, 1, '2026-04-15', -10.0000, 255.00, -2550.00, 20.0000, 5100.00, '202604', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600037, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 520500037, 520000109, 1, '2026-04-15', -20.0000, 8.30, -166.00, 420.0000, 3486.00, '202604', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600038, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 520500038, 520000106, 1, '2026-04-15', -120.0000, 0.25, -30.00, 29880.0000, 7470.00, '202604', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600039, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 520500039, 520000403, 1, '2026-04-15', -10.0000, 6.50, -65.00, 790.0000, 5135.00, '202604', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600040, 1, 1000, 'PROD_USE', 520137001, 'MI202604150001', 520500040, 520000405, 1, '2026-04-15', -20.0000, 0.30, -6.00, 1980.0000, 594.00, '202604', 520000504, '2026-04-15 10:00:00', 520000504, '2026-04-15 10:00:00', 0
  UNION ALL SELECT 520600041, 1, 1000, 'SAL_OUT', 520126003, 'XS202604150001', 520500041, 520000306, 1, '2026-04-15', -8, 280.00, -2240.00, 32, 8960.00, '202604', 520000506, '2026-04-15 14:00:00', 520000506, '2026-04-15 14:00:00', 0
  UNION ALL SELECT 520600042, 1, 1000, 'PUR_IN', 520110004, 'RK202604160001', 520500042, 520000106, 1, '2026-04-16', 20000, 0.25, 5000.00, 49880.0000, 12470.00, '202604', 520000506, '2026-04-16 10:00:00', 520000506, '2026-04-16 10:00:00', 0
  UNION ALL SELECT 520600043, 1, 1000, 'PUR_IN', 520110004, 'RK202604160001', 520500043, 520000105, 1, '2026-04-16', 30000, 0.17, 5100.00, 50000, 8500.00, '202604', 520000506, '2026-04-16 10:00:00', 520000506, '2026-04-16 10:00:00', 0
  UNION ALL SELECT 520600044, 1, 1000, 'PUR_IN', 520110005, 'RK202604170001', 520500044, 520000203, 1, '2026-04-17', 60, 78.00, 4680.00, 110, 8580.00, '202604', 520000506, '2026-04-17 10:00:00', 520000506, '2026-04-17 10:00:00', 0
  UNION ALL SELECT 520600045, 1, 1000, 'SAL_OUT', 520126004, 'XS202604180001', 520500045, 520000302, 1, '2026-04-18', -3, 900.00, -2700.00, 27, 24300.00, '202604', 520000506, '2026-04-18 14:00:00', 520000506, '2026-04-18 14:00:00', 0
  UNION ALL SELECT 520600046, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 520500046, 520000203, 1, '2026-04-20', -30.0000, 78.00, -2340.00, 80.0000, 6240.00, '202604', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520600047, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 520500047, 520000105, 1, '2026-04-20', -120.0000, 0.17, -20.40, 49880.0000, 8479.60, '202604', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520600048, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 520500048, 520000110, 1, '2026-04-20', -60.0000, 15.50, -930.00, 460.0000, 7130.00, '202604', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520600049, 1, 1000, 'PROD_USE', 520137002, 'MI202604200001', 520500049, 520000404, 1, '2026-04-20', -30.0000, 1.20, -36.00, 970.0000, 1164.00, '202604', 520000504, '2026-04-20 10:00:00', 520000504, '2026-04-20 10:00:00', 0
  UNION ALL SELECT 520600050, 1, 1000, 'PUR_IN', 520110002, 'RK202604240001', 520500050, 520000101, 1, '2026-04-24', 1200.0000, 4.55, 5460.00, 4200.0000, 19110.00, '202604', 520000506, '2026-04-24 10:00:00', 520000506, '2026-04-24 10:00:00', 0
  UNION ALL SELECT 520600051, 1, 1000, 'PUR_IN', 520110002, 'RK202604240001', 520500051, 520000102, 1, '2026-04-24', 600.0000, 4.35, 2610.00, 2300.0000, 10005.00, '202604', 520000506, '2026-04-24 10:00:00', 520000506, '2026-04-24 10:00:00', 0
  UNION ALL SELECT 520600052, 1, 1000, 'PROD_IN', 520133001, 'MO202604100001', 520500052, 520000301, 1, '2026-04-28', 10, 750.00, 7500.00, 25, 18750.00, '202604', 520000504, '2026-04-28 16:00:00', 520000504, '2026-04-28 16:00:00', 0
  UNION ALL SELECT 520600053, 1, 1000, 'PROD_IN', 520133002, 'MO202604180001', 520500053, 520000304, 1, '2026-04-30', 30, 150.00, 4500.00, 50, 7500.00, '202604', 520000504, '2026-04-30 16:00:00', 520000504, '2026-04-30 16:00:00', 0
  UNION ALL SELECT 520600054, 1, 1000, 'SAL_OUT', 520126005, 'XS202605100001', 520500054, 520000301, 1, '2026-05-10', -8, 750.00, -6000.00, 17, 12750.00, '202605', 520000506, '2026-05-10 14:00:00', 520000506, '2026-05-10 14:00:00', 0
  UNION ALL SELECT 520600055, 1, 1000, 'SAL_OUT', 520126006, 'XS202605120001', 520500055, 520000304, 1, '2026-05-12', -30, 150.00, -4500.00, 20, 3000.00, '202605', 520000506, '2026-05-12 14:00:00', 520000506, '2026-05-12 14:00:00', 0
  UNION ALL SELECT 520600056, 1, 1000, 'PUR_IN', 520110006, 'RK202605130001', 520500056, 520000103, 1, '2026-05-13', 500, 18.20, 9100.00, 800, 14560.00, '202605', 520000506, '2026-05-13 10:00:00', 520000506, '2026-05-13 10:00:00', 0
  UNION ALL SELECT 520600057, 1, 1000, 'PUR_IN', 520110007, 'RK202605140001', 520500057, 520000111, 1, '2026-05-14', 50, 21.60, 1080.00, 150, 3240.00, '202605', 520000506, '2026-05-14 10:00:00', 520000506, '2026-05-14 10:00:00', 0
  UNION ALL SELECT 520600058, 1, 1000, 'SAL_OUT', 520126007, 'XS202605140001', 520500058, 520000305, 1, '2026-05-14', -12, 215.00, -2580.00, 24, 5160.00, '202605', 520000506, '2026-05-14 14:00:00', 520000506, '2026-05-14 14:00:00', 0
  UNION ALL SELECT 520600059, 1, 1000, 'PUR_IN', 520110008, 'RK202605150001', 520500059, 520000201, 1, '2026-05-15', 30, 380.00, 11400.00, 50.0000, 19000.00, '202605', 520000506, '2026-05-15 10:00:00', 520000506, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600060, 1, 1000, 'PUR_IN', 520110008, 'RK202605150001', 520500060, 520000202, 1, '2026-05-15', 30, 255.00, 7650.00, 50.0000, 12750.00, '202605', 520000506, '2026-05-15 10:00:00', 520000506, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600061, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 520500061, 520000201, 1, '2026-05-15', -12.0000, 380.00, -4560.00, 38.0000, 14440.00, '202605', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600062, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 520500062, 520000202, 1, '2026-05-15', -12.0000, 255.00, -3060.00, 38.0000, 9690.00, '202605', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600063, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 520500063, 520000109, 1, '2026-05-15', -24.0000, 8.30, -199.20, 396.0000, 3286.80, '202605', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600064, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 520500064, 520000106, 1, '2026-05-15', -144.0000, 0.25, -36.00, 49736.0000, 12434.00, '202605', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600065, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 520500065, 520000403, 1, '2026-05-15', -12.0000, 6.50, -78.00, 778.0000, 5057.00, '202605', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600066, 1, 1000, 'PROD_USE', 520137003, 'MI202605150001', 520500066, 520000405, 1, '2026-05-15', -24.0000, 0.30, -7.20, 1956.0000, 586.80, '202605', 520000504, '2026-05-15 10:00:00', 520000504, '2026-05-15 10:00:00', 0
  UNION ALL SELECT 520600067, 1, 1000, 'PUR_IN', 520110009, 'RK202605160001', 520500067, 520000101, 1, '2026-05-16', 2000, 4.55, 9100.00, 6200.0000, 28210.00, '202605', 520000506, '2026-05-16 10:00:00', 520000506, '2026-05-16 10:00:00', 0
  UNION ALL SELECT 520600068, 1, 1000, 'SAL_OUT', 520126008, 'XS202605160001', 520500068, 520000307, 1, '2026-05-16', -6, 330.00, -1980.00, 19, 6270.00, '202605', 520000506, '2026-05-16 14:00:00', 520000506, '2026-05-16 14:00:00', 0
  UNION ALL SELECT 520600069, 1, 1000, 'SAL_OUT', 520126009, 'XS202605200001', 520500069, 520000303, 1, '2026-05-20', -2, 1250.00, -2500.00, 8, 10000.00, '202605', 520000506, '2026-05-20 14:00:00', 520000506, '2026-05-20 14:00:00', 0
  UNION ALL SELECT 520600070, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 520500070, 520000203, 1, '2026-05-21', -40.0000, 78.00, -3120.00, 40.0000, 3120.00, '202605', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520600071, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 520500071, 520000105, 1, '2026-05-21', -160.0000, 0.17, -27.20, 49720.0000, 8452.40, '202605', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520600072, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 520500072, 520000110, 1, '2026-05-21', -80.0000, 15.50, -1240.00, 380.0000, 5890.00, '202605', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520600073, 1, 1000, 'PROD_USE', 520137004, 'MI202605210001', 520500073, 520000404, 1, '2026-05-21', -40.0000, 1.20, -48.00, 930.0000, 1116.00, '202605', 520000504, '2026-05-21 10:00:00', 520000504, '2026-05-21 10:00:00', 0
  UNION ALL SELECT 520600074, 1, 1000, 'SAL_RET', 520130001, 'RT202605220001', 520500074, 520000304, 1, '2026-05-22', 2, 150.00, 300.00, 22, 3300.00, '202605', 520000506, '2026-05-22 15:00:00', 520000506, '2026-05-22 15:00:00', 0
  UNION ALL SELECT 520600075, 1, 1000, 'PROD_IN', 520133003, 'MO202605120001', 520500075, 520000301, 1, '2026-05-24', 12, 750.00, 9000.00, 29, 21750.00, '202605', 520000504, '2026-05-24 16:00:00', 520000504, '2026-05-24 16:00:00', 0
  UNION ALL SELECT 520600076, 1, 1000, 'PROD_IN', 520133004, 'MO202605190001', 520500076, 520000304, 1, '2026-05-26', 40, 150.00, 6000.00, 62, 9300.00, '202605', 520000504, '2026-05-26 16:00:00', 520000504, '2026-05-26 16:00:00', 0
  UNION ALL SELECT 520600077, 1, 1000, 'OTH_OUT', 520143001, 'OO202605280001', 520500077, 520000101, 1, '2026-05-28', -200, 4.55, -910.00, 6000.0000, 27300.00, '202605', 520000506, '2026-05-28 11:00:00', 520000506, '2026-05-28 11:00:00', 0
  UNION ALL SELECT 520600078, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 520500078, 520000201, 1, '2026-06-10', -12.0000, 380.00, -4560.00, 26.0000, 9880.00, '202606', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520600079, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 520500079, 520000202, 1, '2026-06-10', -12.0000, 255.00, -3060.00, 26.0000, 6630.00, '202606', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520600080, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 520500080, 520000109, 1, '2026-06-10', -24.0000, 8.30, -199.20, 372.0000, 3087.60, '202606', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520600081, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 520500081, 520000106, 1, '2026-06-10', -144.0000, 0.25, -36.00, 49592.0000, 12398.00, '202606', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520600082, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 520500082, 520000403, 1, '2026-06-10', -12.0000, 6.50, -78.00, 766.0000, 4979.00, '202606', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520600083, 1, 1000, 'PROD_USE', 520137005, 'MI202606100001', 520500083, 520000405, 1, '2026-06-10', -24.0000, 0.30, -7.20, 1932.0000, 579.60, '202606', 520000504, '2026-06-10 10:00:00', 520000504, '2026-06-10 10:00:00', 0
  UNION ALL SELECT 520600084, 1, 1000, 'SAL_OUT', 520126010, 'XS202606100001', 520500084, 520000301, 1, '2026-06-10', -10, 750.00, -7500.00, 19, 14250.00, '202606', 520000506, '2026-06-10 14:00:00', 520000506, '2026-06-10 14:00:00', 0
  UNION ALL SELECT 520600085, 1, 1000, 'PUR_IN', 520110010, 'RK202606110001', 520500085, 520000108, 1, '2026-06-11', 5000, 0.50, 2500.00, 8000, 4000.00, '202606', 520000506, '2026-06-11 10:00:00', 520000506, '2026-06-11 10:00:00', 0
  UNION ALL SELECT 520600086, 1, 1000, 'PUR_IN', 520110010, 'RK202606110001', 520500086, 520000107, 1, '2026-06-11', 8000, 0.33, 2640.00, 13000, 4290.00, '202606', 520000506, '2026-06-11 10:00:00', 520000506, '2026-06-11 10:00:00', 0
  UNION ALL SELECT 520600087, 1, 1000, 'PUR_IN', 520110011, 'RK202606120001', 520500087, 520000203, 1, '2026-06-12', 80, 78.00, 6240.00, 120.0000, 9360.00, '202606', 520000506, '2026-06-12 10:00:00', 520000506, '2026-06-12 10:00:00', 0
  UNION ALL SELECT 520600088, 1, 1000, 'SAL_OUT', 520126011, 'XS202606120001', 520500088, 520000304, 1, '2026-06-12', -36, 150.00, -5400.00, 26, 3900.00, '202606', 520000506, '2026-06-12 14:00:00', 520000506, '2026-06-12 14:00:00', 0
  UNION ALL SELECT 520600089, 1, 1000, 'PUR_IN', 520110012, 'RK202606130001', 520500089, 520000104, 1, '2026-06-13', 300, 57.00, 17100.00, 450, 25650.00, '202606', 520000506, '2026-06-13 10:00:00', 520000506, '2026-06-13 10:00:00', 0
  UNION ALL SELECT 520600090, 1, 1000, 'PUR_IN', 520110013, 'RK202606140001', 520500090, 520000102, 1, '2026-06-14', 1000, 4.35, 4350.00, 3300.0000, 14355.00, '202606', 520000506, '2026-06-14 10:00:00', 520000506, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520600091, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 520500091, 520000203, 1, '2026-06-14', -45.0000, 78.00, -3510.00, 75.0000, 5850.00, '202606', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520600092, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 520500092, 520000105, 1, '2026-06-14', -180.0000, 0.17, -30.60, 49540.0000, 8421.80, '202606', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520600093, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 520500093, 520000110, 1, '2026-06-14', -90.0000, 15.50, -1395.00, 290.0000, 4495.00, '202606', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520600094, 1, 1000, 'PROD_USE', 520137006, 'MI202606140001', 520500094, 520000404, 1, '2026-06-14', -45.0000, 1.20, -54.00, 885.0000, 1062.00, '202606', 520000504, '2026-06-14 10:00:00', 520000504, '2026-06-14 10:00:00', 0
  UNION ALL SELECT 520600095, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 520500095, 520000203, 1, '2026-06-15', -40.0000, 78.00, -3120.00, 35.0000, 2730.00, '202606', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520600096, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 520500096, 520000105, 1, '2026-06-15', -160.0000, 0.17, -27.20, 49380.0000, 8394.60, '202606', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520600097, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 520500097, 520000110, 1, '2026-06-15', -80.0000, 15.50, -1240.00, 210.0000, 3255.00, '202606', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520600098, 1, 1000, 'PROD_USE', 520139001, 'OS202606100001', 520500098, 520000404, 1, '2026-06-15', -40.0000, 1.20, -48.00, 845.0000, 1014.00, '202606', 520000504, '2026-06-15 09:30:00', 520000504, '2026-06-15 09:30:00', 0
  UNION ALL SELECT 520600099, 1, 1000, 'SAL_OUT', 520126012, 'XS202606150001', 520500099, 520000306, 1, '2026-06-15', -10, 280.00, -2800.00, 22, 6160.00, '202606', 520000506, '2026-06-15 14:00:00', 520000506, '2026-06-15 14:00:00', 0
  UNION ALL SELECT 520600100, 1, 1000, 'SAL_OUT', 520126013, 'XS202606180001', 520500100, 520000302, 1, '2026-06-18', -5, 900.00, -4500.00, 22, 19800.00, '202606', 520000506, '2026-06-18 14:00:00', 520000506, '2026-06-18 14:00:00', 0
  UNION ALL SELECT 520600101, 1, 1000, 'PROD_IN', 520133006, 'MO202606110001', 520500101, 520000304, 1, '2026-06-18', 45, 150.00, 6750.00, 71, 10650.00, '202606', 520000504, '2026-06-18 16:00:00', 520000504, '2026-06-18 16:00:00', 0
  UNION ALL SELECT 520600102, 1, 1000, 'SAL_OUT', 520126014, 'XS202606200001', 520500102, 520000308, 1, '2026-06-20', -3, 440.00, -1320.00, 9, 3960.00, '202606', 520000506, '2026-06-20 14:00:00', 520000506, '2026-06-20 14:00:00', 0
  UNION ALL SELECT 520600103, 1, 1000, 'PUR_RET', 520116001, 'RT202606200001', 520500103, 520000102, 1, '2026-06-20', -60, 4.35, -261.00, 3240.0000, 14094.00, '202606', 520000506, '2026-06-20 14:30:00', 520000506, '2026-06-20 14:30:00', 0
  UNION ALL SELECT 520600104, 1, 1000, 'PROD_IN', 520133005, 'MO202606080001', 520500104, 520000301, 1, '2026-06-20', 12, 750.00, 9000.00, 31, 23250.00, '202606', 520000504, '2026-06-20 16:00:00', 520000504, '2026-06-20 16:00:00', 0
  UNION ALL SELECT 520600105, 1, 1000, 'PROD_IN', 520139001, 'OS202606100001', 520500105, 520000304, 1, '2026-06-25', 40, 150.00, 6000.00, 111, 16650.00, '202606', 520000504, '2026-06-25 10:00:00', 520000504, '2026-06-25 10:00:00', 0
  UNION ALL SELECT 520600106, 1, 1000, 'OTH_OUT', 520143002, 'OO202606250001', 520500106, 520000102, 1, '2026-06-25', -50, 4.35, -217.50, 3190.0000, 13876.50, '202606', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520600107, 1, 1000, 'OTH_OUT', 520143002, 'OO202606250001', 520500107, 520000103, 1, '2026-06-25', -30, 18.20, -546.00, 770, 14014.00, '202606', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520600108, 1, 1000, 'OTH_OUT', 520143002, 'OO202606250001', 520500108, 520000104, 1, '2026-06-25', -20, 57.00, -1140.00, 430, 24510.00, '202606', 520000506, '2026-06-25 11:00:00', 520000506, '2026-06-25 11:00:00', 0
  UNION ALL SELECT 520600109, 1, 1000, 'ADJUST', 520145001, 'CK202606300001', 520500109, 520000405, 1, '2026-06-30', -5, 0.30, -1.50, 1927.0000, 578.10, '202606', 520000506, '2026-06-30 15:00:00', 520000506, '2026-06-30 15:00:00', 0
  UNION ALL SELECT 520600110, 1, 1000, 'ADJUST', 520145001, 'CK202606300001', 520500110, 520000106, 1, '2026-06-30', -200, 0.25, -50.00, 49392.0000, 12348.00, '202606', 520000506, '2026-06-30 15:00:00', 520000506, '2026-06-30 15:00:00', 0
  UNION ALL SELECT 520600111, 1, 1000, 'ADJUST', 520145001, 'CK202606300001', 520500111, 520000101, 1, '2026-06-30', 10, 4.55, 45.50, 6010.0000, 27345.50, '202606', 520000506, '2026-06-30 15:00:00', 520000506, '2026-06-30 15:00:00', 0
  UNION ALL SELECT 520600112, 1, 1000, 'SAL_OUT', 520126015, 'XS202607100001', 520500112, 520000301, 1, '2026-07-10', -4, 750.00, -3000.00, 27, 20250.00, '202607', 520000506, '2026-07-10 14:00:00', 520000506, '2026-07-10 14:00:00', 0
  UNION ALL SELECT 520600113, 1, 1000, 'PUR_IN', 520110014, 'RK202607130001', 520500113, 520000201, 1, '2026-07-13', 20, 380.00, 7600.00, 46.0000, 17480.00, '202607', 520000506, '2026-07-13 10:00:00', 520000506, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600114, 1, 1000, 'PUR_IN', 520110014, 'RK202607130001', 520500114, 520000202, 1, '2026-07-13', 20, 255.00, 5100.00, 46.0000, 11730.00, '202607', 520000506, '2026-07-13 10:00:00', 520000506, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600115, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 520500115, 520000201, 1, '2026-07-13', -6.0000, 380.00, -2280.00, 40.0000, 15200.00, '202607', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600116, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 520500116, 520000202, 1, '2026-07-13', -6.0000, 255.00, -1530.00, 40.0000, 10200.00, '202607', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600117, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 520500117, 520000109, 1, '2026-07-13', -12.0000, 8.30, -99.60, 360.0000, 2988.00, '202607', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600118, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 520500118, 520000106, 1, '2026-07-13', -72.0000, 0.25, -18.00, 49320.0000, 12330.00, '202607', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600119, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 520500119, 520000403, 1, '2026-07-13', -6.0000, 6.50, -39.00, 760.0000, 4940.00, '202607', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600120, 1, 1000, 'PROD_USE', 520137007, 'MI202607130001', 520500120, 520000405, 1, '2026-07-13', -12.0000, 0.30, -3.60, 1915.0000, 574.50, '202607', 520000504, '2026-07-13 10:00:00', 520000504, '2026-07-13 10:00:00', 0
  UNION ALL SELECT 520600121, 1, 1000, 'SAL_OUT', 520126016, 'XS202607130001', 520500121, 520000304, 1, '2026-07-13', -15, 150.00, -2250.00, 96, 14400.00, '202607', 520000506, '2026-07-13 14:00:00', 520000506, '2026-07-13 14:00:00', 0
  UNION ALL SELECT 520600122, 1, 1000, 'PUR_IN', 520110015, 'RK202607140001', 520500122, 520000109, 1, '2026-07-14', 150, 8.30, 1245.00, 510.0000, 4233.00, '202607', 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520600123, 1, 1000, 'PUR_IN', 520110015, 'RK202607140001', 520500123, 520000110, 1, '2026-07-14', 150, 15.50, 2325.00, 360.0000, 5580.00, '202607', 520000506, '2026-07-14 10:00:00', 520000506, '2026-07-14 10:00:00', 0
  UNION ALL SELECT 520600124, 1, 1000, 'PUR_IN', 520110016, 'RK202607150001', 520500124, 520000106, 1, '2026-07-15', 10000, 0.25, 2500.00, 59320.0000, 14830.00, '202607', 520000506, '2026-07-15 10:00:00', 520000506, '2026-07-15 10:00:00', 0
  UNION ALL SELECT 520600125, 1, 1000, 'SAL_OUT', 520126017, 'XS202607150001', 520500125, 520000307, 1, '2026-07-15', -3, 330.00, -990.00, 16, 5280.00, '202607', 520000506, '2026-07-15 14:00:00', 520000506, '2026-07-15 14:00:00', 0
  UNION ALL SELECT 520600126, 1, 1000, 'PUR_IN', 520110017, 'RK202607160001', 520500126, 520000102, 1, '2026-07-16', 800, 4.35, 3480.00, 3990.0000, 17356.50, '202607', 520000506, '2026-07-16 10:00:00', 520000506, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520600127, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 520500127, 520000203, 1, '2026-07-16', -20.0000, 78.00, -1560.00, 15.0000, 1170.00, '202607', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520600128, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 520500128, 520000105, 1, '2026-07-16', -80.0000, 0.17, -13.60, 49300.0000, 8381.00, '202607', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520600129, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 520500129, 520000110, 1, '2026-07-16', -10.0000, 15.50, -155.00, 350.0000, 5425.00, '202607', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520600130, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 520500130, 520000110, 1, '2026-07-16', -30.0000, 15.50, -465.00, 320.0000, 4960.00, '202607', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520600131, 1, 1000, 'PROD_USE', 520137008, 'MI202607160001', 520500131, 520000404, 1, '2026-07-16', -20.0000, 1.20, -24.00, 825.0000, 990.00, '202607', 520000504, '2026-07-16 10:00:00', 520000504, '2026-07-16 10:00:00', 0
  UNION ALL SELECT 520600132, 1, 1000, 'PROD_IN', 520133007, 'MO202607100001', 520500132, 520000301, 1, '2026-07-22', 6, 750.00, 4500.00, 33, 24750.00, '202607', 520000504, '2026-07-22 16:00:00', 520000504, '2026-07-22 16:00:00', 0
  UNION ALL SELECT 520600133, 1, 1000, 'PROD_IN', 520133008, 'MO202607140001', 520500133, 520000304, 1, '2026-07-24', 20, 150.00, 3000.00, 116, 17400.00, '202607', 520000504, '2026-07-24 16:00:00', 520000504, '2026-07-24 16:00:00', 0
  UNION ALL SELECT 520600134, 1, 1000, 'OTH_OUT', 520143003, 'OO202607280001', 520500134, 520000107, 1, '2026-07-28', -500, 0.33, -165.00, 12500, 4125.00, '202607', 520000506, '2026-07-28 11:00:00', 520000506, '2026-07-28 11:00:00', 0
  UNION ALL SELECT 520600135, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 520500135, 520000201, 1, '2026-08-10', -14.0000, 380.00, -5320.00, 26.0000, 9880.00, '202608', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520600136, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 520500136, 520000202, 1, '2026-08-10', -14.0000, 255.00, -3570.00, 26.0000, 6630.00, '202608', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520600137, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 520500137, 520000109, 1, '2026-08-10', -28.0000, 8.30, -232.40, 482.0000, 4000.60, '202608', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520600138, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 520500138, 520000106, 1, '2026-08-10', -168.0000, 0.25, -42.00, 59152.0000, 14788.00, '202608', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520600139, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 520500139, 520000403, 1, '2026-08-10', -14.0000, 6.50, -91.00, 746.0000, 4849.00, '202608', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520600140, 1, 1000, 'PROD_USE', 520137009, 'MI202608100001', 520500140, 520000405, 1, '2026-08-10', -28.0000, 0.30, -8.40, 1887.0000, 566.10, '202608', 520000504, '2026-08-10 10:00:00', 520000504, '2026-08-10 10:00:00', 0
  UNION ALL SELECT 520600141, 1, 1000, 'SAL_OUT', 520126018, 'XS202608100001', 520500141, 520000301, 1, '2026-08-10', -12, 750.00, -9000.00, 21, 15750.00, '202608', 520000506, '2026-08-10 14:00:00', 520000506, '2026-08-10 14:00:00', 0
  UNION ALL SELECT 520600142, 1, 1000, 'PUR_IN', 520110018, 'RK202608120001', 520500142, 520000203, 1, '2026-08-12', 70.0000, 78.00, 5460.00, 85.0000, 6630.00, '202608', 520000506, '2026-08-12 10:00:00', 520000506, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520600143, 1, 1000, 'PUR_IN', 520110018, 'RK202608120001', 520500143, 520000201, 1, '2026-08-12', 14.0000, 380.00, 5320.00, 40.0000, 15200.00, '202608', 520000506, '2026-08-12 10:00:00', 520000506, '2026-08-12 10:00:00', 0
  UNION ALL SELECT 520600144, 1, 1000, 'SAL_OUT', 520126019, 'XS202608120001', 520500144, 520000302, 1, '2026-08-12', -8, 900.00, -7200.00, 14, 12600.00, '202608', 520000506, '2026-08-12 14:00:00', 520000506, '2026-08-12 14:00:00', 0
  UNION ALL SELECT 520600145, 1, 1000, 'PUR_IN', 520110020, 'RK202608130001', 520500145, 520000109, 1, '2026-08-13', 400, 8.30, 3320.00, 882.0000, 7320.60, '202608', 520000506, '2026-08-13 10:00:00', 520000506, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520600146, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 520500146, 520000203, 1, '2026-08-13', -55.0000, 78.00, -4290.00, 30.0000, 2340.00, '202608', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520600147, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 520500147, 520000105, 1, '2026-08-13', -220.0000, 0.17, -37.40, 49080.0000, 8343.60, '202608', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520600148, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 520500148, 520000110, 1, '2026-08-13', -110.0000, 15.50, -1705.00, 210.0000, 3255.00, '202608', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520600149, 1, 1000, 'PROD_USE', 520137010, 'MI202608130001', 520500149, 520000404, 1, '2026-08-13', -55.0000, 1.20, -66.00, 770.0000, 924.00, '202608', 520000504, '2026-08-13 10:00:00', 520000504, '2026-08-13 10:00:00', 0
  UNION ALL SELECT 520600150, 1, 1000, 'PUR_IN', 520110021, 'RK202608140001', 520500150, 520000101, 1, '2026-08-14', 2500, 4.55, 11375.00, 8510.0000, 38720.50, '202608', 520000506, '2026-08-14 10:00:00', 520000506, '2026-08-14 10:00:00', 0
  UNION ALL SELECT 520600151, 1, 1000, 'SAL_OUT', 520126020, 'XS202608140001', 520500151, 520000304, 1, '2026-08-14', -40, 150.00, -6000.00, 76, 11400.00, '202608', 520000506, '2026-08-14 14:00:00', 520000506, '2026-08-14 14:00:00', 0
  UNION ALL SELECT 520600152, 1, 1000, 'PUR_IN', 520110022, 'RK202608150001', 520500152, 520000103, 1, '2026-08-15', 300, 18.20, 5460.00, 1070, 19474.00, '202608', 520000506, '2026-08-15 10:00:00', 520000506, '2026-08-15 10:00:00', 0
  UNION ALL SELECT 520600153, 1, 1000, 'SAL_OUT', 520126021, 'XS202608150001', 520500153, 520000305, 1, '2026-08-15', -18, 215.00, -3870.00, 6, 1290.00, '202608', 520000506, '2026-08-15 14:00:00', 520000506, '2026-08-15 14:00:00', 0
  UNION ALL SELECT 520600154, 1, 1000, 'SAL_OUT', 520126022, 'XS202608180001', 520500154, 520000306, 1, '2026-08-18', -12, 280.00, -3360.00, 10, 2800.00, '202608', 520000506, '2026-08-18 14:00:00', 520000506, '2026-08-18 14:00:00', 0
  UNION ALL SELECT 520600155, 1, 1000, 'SAL_OUT', 520126023, 'XS202608200001', 520500155, 520000308, 1, '2026-08-20', -5, 440.00, -2200.00, 4, 1760.00, '202608', 520000506, '2026-08-20 14:00:00', 520000506, '2026-08-20 14:00:00', 0
  UNION ALL SELECT 520600156, 1, 1000, 'PROD_IN', 520133010, 'MO202608030001', 520500156, 520000304, 1, '2026-08-20', 55, 150.00, 8250.00, 131, 19650.00, '202608', 520000504, '2026-08-20 16:00:00', 520000504, '2026-08-20 16:00:00', 0
  UNION ALL SELECT 520600157, 1, 1000, 'SAL_RET', 520130002, 'RT202608220001', 520500157, 520000301, 1, '2026-08-22', 1, 750.00, 750.00, 22, 16500.00, '202608', 520000506, '2026-08-22 15:00:00', 520000506, '2026-08-22 15:00:00', 0
  UNION ALL SELECT 520600158, 1, 1000, 'PROD_IN', 520133009, 'MO202608060001', 520500158, 520000301, 1, '2026-08-22', 14, 750.00, 10500.00, 36, 27000.00, '202608', 520000504, '2026-08-22 16:00:00', 520000504, '2026-08-22 16:00:00', 0
  UNION ALL SELECT 520600159, 1, 1000, 'OTH_OUT', 520143004, 'OO202608240001', 520500159, 520000301, 1, '2026-08-24', -2, 750.00, -1500.00, 34, 25500.00, '202608', 520000506, '2026-08-24 11:00:00', 520000506, '2026-08-24 11:00:00', 0
  UNION ALL SELECT 520600160, 1, 1000, 'OTH_OUT', 520143005, 'OO202608250001', 520500160, 520000401, 1, '2026-08-25', -8, 95.00, -760.00, 142, 13490.00, '202608', 520000506, '2026-08-25 11:00:00', 520000506, '2026-08-25 11:00:00', 0
  UNION ALL SELECT 520600161, 1, 1000, 'OTH_OUT', 520143005, 'OO202608250001', 520500161, 520000402, 1, '2026-08-25', -5, 12.00, -60.00, 75, 900.00, '202608', 520000506, '2026-08-25 11:00:00', 520000506, '2026-08-25 11:00:00', 0
  UNION ALL SELECT 520600162, 1, 1000, 'PUR_IN', 520110019, 'RK202608260001', 520500162, 520000203, 1, '2026-08-26', 30.0000, 78.00, 2340.00, 60.0000, 4680.00, '202608', 520000506, '2026-08-26 10:00:00', 520000506, '2026-08-26 10:00:00', 0
  UNION ALL SELECT 520600163, 1, 1000, 'PUR_IN', 520110019, 'RK202608260001', 520500163, 520000201, 1, '2026-08-26', 6.0000, 380.00, 2280.00, 46.0000, 17480.00, '202608', 520000506, '2026-08-26 10:00:00', 520000506, '2026-08-26 10:00:00', 0
  UNION ALL SELECT 520600164, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520500164, 520000301, 1, '2026-08-27', -20, 750.00, -15000.00, 14, 10500.00, '202608', 520000506, '2026-08-27 10:30:00', 520000506, '2026-08-27 10:30:00', 0
  UNION ALL SELECT 520600165, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520500165, 520000301, 520010301, '2026-08-27', 20, 750.00, 15000.00, 34, 25500.00, '202608', 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520600166, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520500166, 520000302, 1, '2026-08-27', -8, 900.00, -7200.00, 6, 5400.00, '202608', 520000506, '2026-08-27 10:30:00', 520000506, '2026-08-27 10:30:00', 0
  UNION ALL SELECT 520600167, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520500167, 520000302, 520010301, '2026-08-27', 8, 900.00, 7200.00, 14, 12600.00, '202608', 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520600168, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520500168, 520000304, 1, '2026-08-27', -50, 150.00, -7500.00, 81, 12150.00, '202608', 520000506, '2026-08-27 10:30:00', 520000506, '2026-08-27 10:30:00', 0
  UNION ALL SELECT 520600169, 1, 1000, 'MOVE', NULL, 'TR202608270001', 520500169, 520000304, 520010301, '2026-08-27', 50, 150.00, 7500.00, 131, 19650.00, '202608', 520000506, '2026-08-27 10:35:00', 520000506, '2026-08-27 10:35:00', 0
  UNION ALL SELECT 520600170, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 520500170, 520000201, 1, '2026-09-01', -10.0000, 380.00, -3800.00, 36.0000, 13680.00, '202609', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520600171, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 520500171, 520000202, 1, '2026-09-01', -10.0000, 255.00, -2550.00, 16.0000, 4080.00, '202609', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520600172, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 520500172, 520000109, 1, '2026-09-01', -20.0000, 8.30, -166.00, 862.0000, 7154.60, '202609', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520600173, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 520500173, 520000106, 1, '2026-09-01', -120.0000, 0.25, -30.00, 59032.0000, 14758.00, '202609', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520600174, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 520500174, 520000403, 1, '2026-09-01', -10.0000, 6.50, -65.00, 736.0000, 4784.00, '202609', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520600175, 1, 1000, 'PROD_USE', 520137011, 'MI202609010001', 520500175, 520000405, 1, '2026-09-01', -20.0000, 0.30, -6.00, 1867.0000, 560.10, '202609', 520000504, '2026-09-01 10:00:00', 520000504, '2026-09-01 10:00:00', 0
  UNION ALL SELECT 520600176, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 520500176, 520000203, 1, '2026-09-02', -30.0000, 78.00, -2340.00, 30.0000, 2340.00, '202609', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520600177, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 520500177, 520000105, 1, '2026-09-02', -120.0000, 0.17, -20.40, 48960.0000, 8323.20, '202609', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520600178, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 520500178, 520000110, 1, '2026-09-02', -60.0000, 15.50, -930.00, 150.0000, 2325.00, '202609', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520600179, 1, 1000, 'PROD_USE', 520137012, 'MI202609020001', 520500179, 520000404, 1, '2026-09-02', -30.0000, 1.20, -36.00, 740.0000, 888.00, '202609', 520000504, '2026-09-02 10:00:00', 520000504, '2026-09-02 10:00:00', 0
  UNION ALL SELECT 520600180, 1, 1000, 'SAL_OUT', 520126024, 'XS202609020001', 520500180, 520000301, 1, '2026-09-02', -10, 750.00, -7500.00, 24, 18000.00, '202609', 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520600181, 1, 1000, 'SAL_OUT', 520126025, 'XS202609020002', 520500181, 520000302, 1, '2026-09-02', -6, 900.00, -5400.00, 8, 7200.00, '202609', 520000506, '2026-09-02 14:00:00', 520000506, '2026-09-02 14:00:00', 0
  UNION ALL SELECT 520600182, 1, 1000, 'PUR_IN', 520110023, 'RK202609030001', 520500182, 520000201, 1, '2026-09-03', 15, 380.00, 5700.00, 51.0000, 19380.00, '202609', 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-03 10:00:00', 0
  UNION ALL SELECT 520600183, 1, 1000, 'PUR_IN', 520110023, 'RK202609030001', 520500183, 520000202, 1, '2026-09-03', 15, 255.00, 3825.00, 31.0000, 7905.00, '202609', 520000506, '2026-09-03 10:00:00', 520000506, '2026-09-03 10:00:00', 0
  UNION ALL SELECT 520600184, 1, 1000, 'SAL_OUT', 520126026, 'XS202609030001', 520500184, 520000304, 1, '2026-09-03', -32, 150.00, -4800.00, 99, 14850.00, '202609', 520000506, '2026-09-03 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520600185, 1, 1000, 'SAL_OUT', 520126027, 'XS202609030002', 520500185, 520000303, 1, '2026-09-03', -3, 1250.00, -3750.00, 5, 6250.00, '202609', 520000506, '2026-09-03 14:00:00', 520000506, '2026-09-03 14:00:00', 0
  UNION ALL SELECT 520600186, 1, 1000, 'PROD_IN', 520133011, 'MO202608300001', 520500186, 520000301, 1, '2026-09-03', 10, 750.00, 7500.00, 34, 25500.00, '202609', 520000504, '2026-09-03 16:00:00', 520000504, '2026-09-03 16:00:00', 0
  UNION ALL SELECT 520600187, 1, 1000, 'PUR_IN', 520110024, 'RK202609040001', 520500187, 520000105, 1, '2026-09-04', 20000, 0.17, 3400.00, 68960.0000, 11723.20, '202609', 520000506, '2026-09-04 10:00:00', 520000506, '2026-09-04 10:00:00', 0
  UNION ALL SELECT 520600188, 1, 1000, 'PUR_IN', 520110025, 'RK202609040002', 520500188, 520000110, 1, '2026-09-04', 200, 15.50, 3100.00, 350.0000, 5425.00, '202609', 520000506, '2026-09-04 10:00:00', 520000506, '2026-09-04 10:00:00', 0
  UNION ALL SELECT 520600189, 1, 1000, 'SAL_OUT', 520126028, 'XS202609040001', 520500189, 520000307, 1, '2026-09-04', -8, 330.00, -2640.00, 8, 2640.00, '202609', 520000506, '2026-09-04 14:00:00', 520000506, '2026-09-04 14:00:00', 0
  UNION ALL SELECT 520600190, 1, 1000, 'PROD_IN', 520133012, 'MO202609010001', 520500190, 520000304, 1, '2026-09-04', 20, 150.00, 3000.00, 119, 17850.00, '202609', 520000504, '2026-09-04 16:00:00', 520000504, '2026-09-04 16:00:00', 0
) _v WHERE @g = 0;

-- ---- sys_role_menu : 管理层角色菜单(复制超管) ----
SET @g := (SELECT COUNT(*) FROM `sys_role_menu` WHERE `role_id` = 520000510 AND `deleted` = 0);
INSERT INTO `sys_role_menu` (`org_id`,`tenant_id`,`role_id`,`menu_id`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
SELECT 0, 1000, 520000510, `menu_id`, 520000505, '2026-03-01 09:00:00', 520000505, '2026-03-01 09:00:00', 0
FROM `sys_role_menu` WHERE `role_id` = 1 AND `deleted` = 0 AND @g = 0;

-- ---- fin_account : 回写工行基本户期末余额 ----
SET @g := (SELECT COUNT(*) FROM `fin_account_flow` WHERE `id` BETWEEN 520000000 AND 529999999);
UPDATE `fin_account` SET `current_balance` = 494510.00, `update_by` = 520000505, `update_time` = '2026-09-04 16:00:00' WHERE `id` = 2 AND @g > 0;

