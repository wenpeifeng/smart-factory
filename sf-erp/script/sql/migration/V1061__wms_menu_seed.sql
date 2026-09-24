-- =====================================================================
-- V1061: WMS 菜单与权限码种子（自 V421 迁移至此）
-- 原因：sys_menu 的 org_id/tenant_id 列由 V1012 添加，种子必须使用 >1012 的
--       版本号，保证全新库按序执行不报 Unknown column。幂等可重放。
-- =====================================================================

-- ---------- 11. sys_menu 种子：WMS 菜单与权限码（挂库存管理 230） ----------
-- 236 作业任务 / 237 波次管理 / 238 序列号管理 / 239 ABC分级(无独立页面,权限码承载)
INSERT IGNORE INTO `sys_menu` (`id`, `org_id`, `tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(236,  0, 1000, 230, '作业任务', 2, 'inventory/tasks', 'inventory/tasks/TaskList', 'inventory:wms:task:list', NULL, 6, 1, 1),
(2361, 0, 1000, 236, '任务执行', 3, NULL, NULL, 'inventory:wms:task:edit',  NULL, 1, 1, 1),
(2362, 0, 1000, 236, '任务完成核验', 3, NULL, NULL, 'inventory:wms:task:audit', NULL, 2, 1, 1),
(237,  0, 1000, 230, '波次管理', 2, 'inventory/waves', 'inventory/waves/WaveList', 'inventory:wms:wave:list', NULL, 7, 1, 1),
(2371, 0, 1000, 237, '波次创建执行', 3, NULL, NULL, 'inventory:wms:wave:edit',  NULL, 1, 1, 1),
(2372, 0, 1000, 237, '波次完成', 3, NULL, NULL, 'inventory:wms:wave:audit', NULL, 2, 1, 1),
(238,  0, 1000, 230, '序列号管理', 2, 'inventory/serials', 'inventory/serials/SerialList', 'inventory:wms:serial:list', NULL, 8, 1, 1),
(2381, 0, 1000, 238, '序列号登记', 3, NULL, NULL, 'inventory:wms:serial:edit',  NULL, 1, 1, 1),
(2382, 0, 1000, 238, '序列号核销', 3, NULL, NULL, 'inventory:wms:serial:audit', NULL, 2, 1, 1),
(239,  0, 1000, 230, 'ABC分级', 2, 'inventory/tasks', NULL, 'inventory:wms:abc:list', NULL, 9, 1, 1),
(2391, 0, 1000, 239, 'ABC重算', 3, NULL, NULL, 'inventory:wms:abc:edit',  NULL, 1, 1, 1),
(2392, 0, 1000, 239, '循环盘点生成', 3, NULL, NULL, 'inventory:wms:abc:audit', NULL, 2, 1, 1);

-- super_admin(role_id=1) 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (236, 2361, 2362, 237, 2371, 2372, 238, 2381, 2382, 239, 2391, 2392)
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.role_id = 1 AND rm.menu_id = `sys_menu`.`id`);
