-- =====================================================================
-- V19: P4 呈现层菜单与权限码（看板大屏/驾驶舱/报表中心）
-- =====================================================================

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(372, 370, '车间电子看板', 2, 'kanban/screen', NULL, 'report:screen:query', NULL, 2, 1, 1),
(390, 0, '管理驾驶舱', 2, 'cockpit', NULL, 'report:cockpit:query', 'FundOutlined', 950, 1, 1),
(391, 0, '报表中心', 1, 'report', NULL, NULL, 'FileTextOutlined', 960, 1, 1),
(392, 391, '工单达成', 2, 'report/workorders', NULL, 'report:workorder:query', NULL, 1, 1, 1),
(393, 392, '工单达成导出', 3, NULL, NULL, 'report:workorder:export', NULL, 1, 1, 1),
(394, 391, '报工明细', 2, 'report/stations', NULL, 'report:station:query', NULL, 2, 1, 1),
(395, 394, '报工明细导出', 3, NULL, NULL, 'report:station:export', NULL, 1, 1, 1),
(396, 391, '质量统计', 2, 'report/quality', NULL, 'report:quality:query', NULL, 3, 1, 1);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` BETWEEN 372 AND 396
  AND `id` NOT IN (SELECT `menu_id` FROM `sys_role_menu` WHERE `role_id` = 1);
