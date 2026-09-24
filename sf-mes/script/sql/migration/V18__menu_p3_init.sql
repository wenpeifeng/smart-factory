-- =====================================================================
-- V18: P3 模块菜单与权限码初始化（追溯/SPC/设备/安灯）
-- 全部授权给角色 1（super_admin）；普通角色由管理端按需分配。
-- =====================================================================

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
-- ---------- 追溯（P3） ----------
(330, 0, '追溯管理', 1, 'trace', NULL, NULL, 'AimOutlined', 700, 1, 1),
(331, 330, '正反向追溯', 2, 'trace/links', NULL, 'trace:link:query', NULL, 1, 1, 1),
(332, 330, '电子批记录', 2, 'trace/ebrs', NULL, 'trace:ebr:list', NULL, 2, 1, 1),
(341, 332, '生成批记录', 3, NULL, NULL, 'trace:ebr:generate', NULL, 1, 1, 1),
-- ---------- SPC（P3，挂质量管理） ----------
(304, 300, 'SPC控制图', 2, 'quality/spc', NULL, 'quality:spc:list', NULL, 4, 1, 1),
(318, 304, 'SPC规则维护', 3, NULL, NULL, 'quality:spc:edit', NULL, 1, 1, 1),
-- ---------- 设备（P3） ----------
(350, 0, '设备管理', 1, 'equipment', NULL, NULL, 'ToolOutlined', 800, 1, 1),
(351, 350, '设备台账', 2, 'equipment/devices', NULL, 'equipment:device:list', NULL, 1, 1, 1),
(361, 351, '设备新增', 3, NULL, NULL, 'equipment:device:add', NULL, 1, 1, 1),
(362, 351, '设备修改', 3, NULL, NULL, 'equipment:device:edit', NULL, 2, 1, 1),
(363, 351, '设备删除', 3, NULL, NULL, 'equipment:device:delete', NULL, 3, 1, 1),
(352, 350, '点检记录', 2, 'equipment/checks', NULL, 'equipment:check:list', NULL, 2, 1, 1),
(364, 352, '登记点检', 3, NULL, NULL, 'equipment:check:add', NULL, 1, 1, 1),
(353, 350, '保养维修', 2, 'equipment/maintains', NULL, 'equipment:maintain:list', NULL, 3, 1, 1),
(365, 353, '创建保养维修', 3, NULL, NULL, 'equipment:maintain:add', NULL, 1, 1, 1),
(366, 353, '完成保养维修', 3, NULL, NULL, 'equipment:maintain:finish', NULL, 2, 1, 1),
(354, 350, 'OEE分析', 2, 'equipment/oee', NULL, 'equipment:oee:query', NULL, 4, 1, 1),
-- ---------- 看板/安灯（P3） ----------
(370, 0, '车间看板', 1, 'kanban', NULL, NULL, 'AlertOutlined', 900, 1, 1),
(371, 370, '安灯呼叫', 2, 'kanban/andons', NULL, 'kanban:andon:list', NULL, 1, 1, 1),
(381, 371, '发起呼叫', 3, NULL, NULL, 'kanban:andon:call', NULL, 1, 1, 1),
(382, 371, '响应呼叫', 3, NULL, NULL, 'kanban:andon:respond', NULL, 2, 1, 1),
(383, 371, '关闭呼叫', 3, NULL, NULL, 'kanban:andon:close', NULL, 3, 1, 1);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` >= 300 AND `id` < 400 AND `id` NOT IN (SELECT `menu_id` FROM `sys_role_menu` WHERE `role_id` = 1);
