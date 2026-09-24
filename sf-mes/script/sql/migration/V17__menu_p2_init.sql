-- =====================================================================
-- V17: P2 执行核心菜单与权限码初始化（补 P1 缺口 basedata + P2 五模块）
-- 内容：
--   1) 基础资料/计划/执行/物料/质量/集成 六组菜单（页面 type=2 + 按钮 type=3）
--   2) 全部授权给角色 1（super_admin），普通角色由管理端按需分配
-- 说明：MES 业务门户菜单前端硬编码（MainLayout），sys_menu 这里主要承载
--       按钮级权限码（@RequirePerm / 前端 HasPerm 同源）与角色授权。
-- =====================================================================

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
-- ---------- 基础资料（P1 补种） ----------
(200, 0, '基础资料', 1, 'basedata', NULL, NULL, 'DatabaseOutlined', 100, 1, 1),
(201, 200, '物料管理', 2, 'basedata/materials', NULL, 'basedata:material:list', NULL, 1, 1, 1),
(211, 201, '物料新增', 3, NULL, NULL, 'basedata:material:add', NULL, 1, 1, 1),
(212, 201, '物料修改', 3, NULL, NULL, 'basedata:material:edit', NULL, 2, 1, 1),
(213, 201, '物料删除', 3, NULL, NULL, 'basedata:material:delete', NULL, 3, 1, 1),
(202, 200, '客户管理', 2, 'basedata/customers', NULL, 'basedata:customer:list', NULL, 2, 1, 1),
(203, 200, '供应商管理', 2, 'basedata/suppliers', NULL, 'basedata:supplier:list', NULL, 3, 1, 1),
(204, 200, '仓库管理', 2, 'basedata/warehouses', NULL, 'basedata:warehouse:list', NULL, 4, 1, 1),
-- ---------- 计划管理（P2） ----------
(220, 0, '计划管理', 1, 'plan', NULL, NULL, 'ScheduleOutlined', 200, 1, 1),
(221, 220, '生产工单', 2, 'plan/workorders', NULL, 'plan:workorder:list', NULL, 1, 1, 1),
(231, 221, '工单新增', 3, NULL, NULL, 'plan:workorder:add', NULL, 1, 1, 1),
(232, 221, '工单修改', 3, NULL, NULL, 'plan:workorder:edit', NULL, 2, 1, 1),
(233, 221, '工单删除', 3, NULL, NULL, 'plan:workorder:delete', NULL, 3, 1, 1),
(234, 221, '工单下达', 3, NULL, NULL, 'plan:workorder:release', NULL, 4, 1, 1),
(235, 221, '工单完工', 3, NULL, NULL, 'plan:workorder:finish', NULL, 5, 1, 1),
(236, 221, '工单关闭', 3, NULL, NULL, 'plan:workorder:close', NULL, 6, 1, 1),
(237, 221, '工序派工', 3, NULL, NULL, 'plan:step:dispatch', NULL, 7, 1, 1),
(222, 220, '齐套分析', 2, 'plan/kit-checks', NULL, 'plan:kitcheck:list', NULL, 2, 1, 1),
(238, 222, '执行齐套分析', 3, NULL, NULL, 'plan:kitcheck:run', NULL, 1, 1, 1),
(239, 220, '排程时间片', 2, NULL, NULL, 'plan:schedule:list', NULL, 3, 1, 1),
(240, 220, '排程编辑', 3, NULL, NULL, 'plan:schedule:edit', NULL, 4, 1, 1),
-- ---------- 生产执行（P2） ----------
(260, 0, '生产执行', 1, 'execution', NULL, NULL, 'RocketOutlined', 300, 1, 1),
(261, 260, '过站报工', 2, 'execution/stations', NULL, 'execution:station:list', NULL, 1, 1, 1),
(271, 261, '扫码报工', 3, NULL, NULL, 'execution:station:report', NULL, 1, 1, 1),
(262, 260, '在制品WIP', 2, 'execution/wips', NULL, 'execution:wip:list', NULL, 2, 1, 1),
(263, 260, '返工报废', 2, 'execution/rework-scraps', NULL, 'execution:reworkscrap:list', NULL, 3, 1, 1),
(272, 263, '返工报废新增', 3, NULL, NULL, 'execution:reworkscrap:add', NULL, 1, 1, 1),
(273, 263, '返工报废处理', 3, NULL, NULL, 'execution:reworkscrap:handle', NULL, 2, 1, 1),
-- ---------- 车间物料（P2） ----------
(280, 0, '车间物料', 1, 'material', NULL, NULL, 'InboxOutlined', 400, 1, 1),
(281, 280, '领料单', 2, 'material/pickings', NULL, 'material:picking:list', NULL, 1, 1, 1),
(291, 281, '领料新增', 3, NULL, NULL, 'material:picking:add', NULL, 1, 1, 1),
(292, 281, '领料删除', 3, NULL, NULL, 'material:picking:delete', NULL, 2, 1, 1),
(293, 281, '领料审核', 3, NULL, NULL, 'material:picking:audit', NULL, 3, 1, 1),
(282, 280, '退料单', 2, 'material/returns', NULL, 'material:return:list', NULL, 2, 1, 1),
(294, 282, '退料新增', 3, NULL, NULL, 'material:return:add', NULL, 1, 1, 1),
(295, 282, '退料删除', 3, NULL, NULL, 'material:return:delete', NULL, 2, 1, 1),
(296, 282, '退料审核', 3, NULL, NULL, 'material:return:audit', NULL, 3, 1, 1),
(283, 280, '线边仓库存', 2, 'material/shop-stocks', NULL, 'material:shopstock:list', NULL, 3, 1, 1),
(284, 280, '批次SN档案', 2, 'material/batch-sns', NULL, 'material:batchsn:list', NULL, 4, 1, 1),
(297, 284, '批次SN登记', 3, NULL, NULL, 'material:batchsn:add', NULL, 1, 1, 1),
(298, 284, '标签打印', 3, NULL, NULL, 'material:label:print', NULL, 2, 1, 1),
-- ---------- 质量管理（P2） ----------
(300, 0, '质量管理', 1, 'quality', NULL, NULL, 'ExperimentOutlined', 500, 1, 1),
(301, 300, '检验模板', 2, 'quality/templates', NULL, 'quality:template:list', NULL, 1, 1, 1),
(311, 301, '模板新增', 3, NULL, NULL, 'quality:template:add', NULL, 1, 1, 1),
(312, 301, '模板修改', 3, NULL, NULL, 'quality:template:edit', NULL, 2, 1, 1),
(313, 301, '模板删除', 3, NULL, NULL, 'quality:template:delete', NULL, 3, 1, 1),
(302, 300, '检验单', 2, 'quality/inspects', NULL, 'quality:inspect:list', NULL, 2, 1, 1),
(314, 302, '检验单新增', 3, NULL, NULL, 'quality:inspect:add', NULL, 1, 1, 1),
(315, 302, '实测值录入', 3, NULL, NULL, 'quality:inspect:fill', NULL, 2, 1, 1),
(316, 302, '检验单提交', 3, NULL, NULL, 'quality:inspect:submit', NULL, 3, 1, 1),
(303, 300, '不合格处置', 2, 'quality/rejects', NULL, 'quality:reject:list', NULL, 3, 1, 1),
(317, 303, '处置单新增', 3, NULL, NULL, 'quality:reject:add', NULL, 1, 1, 1),
-- ---------- 系统集成（P2） ----------
(320, 0, '系统集成', 1, 'integration', NULL, NULL, 'ApiOutlined', 600, 1, 1),
(321, 320, '回传推送日志', 2, 'integration/push-logs', NULL, 'integration:pushlog:list', NULL, 1, 1, 1),
(322, 320, '用户同步', 3, NULL, NULL, 'integration:authsync:run', NULL, 2, 1, 1);

-- 授权给 super_admin（角色 1）；基于ata 等普通角色由管理端按需分配
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` >= 200;
