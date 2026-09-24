-- =====================================================================
-- V1201: demo-data 演示数据 —— 演示角色 + 演示账号（密码统一 demo123456）
-- 内容：
--   1) 演示角色：车间操作工/质检员/仓库管理员（按 V17/V18 权限码组装 sys_role_menu）
--   2) 演示账号：operator/qc/wh（绑定角色），用于演示与培训
-- 注意：主账号生命周期归 auth-center（见 AGENTS 决策 2），本脚本仅在
--       auth-center 未接入的 stub 阶段提供本地演示登录。
-- =====================================================================

-- 1. 演示角色
INSERT INTO `sys_role` (`id`, `name`, `code`, `sort`, `status`, `remark`)
VALUES (10, '车间操作工(演示)', 'demo_operator', 10, 1, '过站报工/在制品查看/安灯呼叫'),
       (11, '质检员(演示)',     'demo_qc',       11, 1, '检验模板/检验单/不合格处置/SPC 查看'),
       (12, '仓库管理员(演示)', 'demo_wh',       12, 1, '领退料/线边仓/批次SN');

-- 2. 角色授权（菜单/按钮 id 取自 V17/V18）
-- 车间操作工：执行查询+报工、WIP、安灯呼叫/查看
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 10, `id` FROM `sys_menu`
WHERE `perms` IN ('execution:station:list', 'execution:station:report', 'execution:wip:list',
                  'plan:workorder:list', 'kanban:andon:list', 'kanban:andon:call');

-- 质检员：质量模块查询+检验操作+SPC 查看
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 11, `id` FROM `sys_menu`
WHERE `perms` IN ('quality:template:list', 'quality:inspect:list', 'quality:inspect:add',
                  'quality:inspect:fill', 'quality:inspect:submit',
                  'quality:reject:list', 'quality:reject:add', 'quality:spc:list');

-- 仓库管理员：领退料全套 + 线边仓 + 批次SN
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 12, `id` FROM `sys_menu`
WHERE `perms` IN ('material:picking:list', 'material:picking:add', 'material:picking:audit', 'material:picking:delete',
                  'material:return:list', 'material:return:add', 'material:return:audit', 'material:return:delete',
                  'material:shopstock:list', 'material:batchsn:list', 'material:batchsn:add', 'material:label:print',
                  'basedata:material:list');

-- 3. 演示账号（密码 demo123456，BCrypt）
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `password`, `org_id`, `data_scope`, `status`, `remark`)
VALUES (10, 'operator', '王操作(演示)', '$2a$10$592LpadS6ch8TdBwlRoSxOOQl31oxTWMCJNSQ1JEkG2AbRJUl2LAW', 1, 'SELF', 1, '演示账号'),
       (11, 'qc',       '李质检(演示)', '$2a$10$592LpadS6ch8TdBwlRoSxOOQl31oxTWMCJNSQ1JEkG2AbRJUl2LAW', 1, 'SELF', 1, '演示账号'),
       (12, 'wh',       '赵仓管(演示)', '$2a$10$592LpadS6ch8TdBwlRoSxOOQl31oxTWMCJNSQ1JEkG2AbRJUl2LAW', 1, 'SELF', 1, '演示账号');

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (10, 10), (11, 11), (12, 12);
