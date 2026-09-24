-- =====================================================================
-- V15: 系统基座初始数据
-- 内容：
--   1. 部门树样例（总公司/技术部/财务部）；
--   2. 内置角色 super_admin（超级管理员，代码层直接放行全部菜单）+ common 演示角色；
--   3. 系统管理菜单树（目录/菜单/按钮三级）；
--   4. admin 用户绑定 super_admin；
--   5. 修正 V1 种子数据中 admin 的口令密文（原密文与 admin123 不匹配，统一重置）；
--   6. 内置通用字典（通用状态/是否）。
-- =====================================================================

-- 1. 部门
INSERT INTO `sys_dept` (`id`, `parent_id`, `name`, `sort`, `leader`, `status`) VALUES
(1, 0, '总公司',   0, '管理员', 1),
(2, 1, '技术部',   1, NULL,    1),
(3, 1, '财务部',   2, NULL,    1);

-- 2. 角色
INSERT INTO `sys_role` (`id`, `name`, `code`, `sort`, `status`, `remark`) VALUES
(1, '超级管理员', 'super_admin', 0, 1, '内置角色：拥有全部菜单与按钮权限，不可删除'),
(2, '普通用户',   'common',      1, 1, '演示角色：默认未分配菜单');

-- 3. 菜单（目录/菜单/按钮三级；path 存完整路由不带斜杠，component 存前端组件路径）
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(1,   0,  '系统管理', 1, 'system',      NULL,                      NULL,                'TeamOutlined', 900, 1, 1),
(11,  1,  '用户管理', 2, 'system/users', 'system/users/UserList', 'system:user:list', NULL, 1, 1, 1),
(12,  1,  '角色管理', 2, 'system/roles', 'system/roles/RoleList', 'system:role:list', NULL, 2, 1, 1),
(13,  1,  '菜单管理', 2, 'system/menus', 'system/menus/MenuList', 'system:menu:list', NULL, 3, 1, 1),
(14,  1,  '部门管理', 2, 'system/depts', 'system/depts/DeptList', 'system:dept:list', NULL, 4, 1, 1),
(15,  1,  '字典管理', 2, 'system/dicts', 'system/dicts/DictPage', 'system:dict:list', NULL, 5, 1, 1),
(16,  1,  '操作日志', 2, 'system/logs',  'system/logs/OperLogList', 'system:log:list', NULL, 6, 1, 1),
(111, 11, '用户新增', 3, NULL,           NULL, 'system:user:add',      NULL, 1, 1, 1),
(112, 11, '用户修改', 3, NULL,           NULL, 'system:user:edit',     NULL, 2, 1, 1),
(113, 11, '用户删除', 3, NULL,           NULL, 'system:user:delete',   NULL, 3, 1, 1),
(114, 11, '重置密码', 3, NULL,           NULL, 'system:user:resetPwd', NULL, 4, 1, 1),
(121, 12, '角色新增', 3, NULL,           NULL, 'system:role:add',      NULL, 1, 1, 1),
(122, 12, '角色修改', 3, NULL,           NULL, 'system:role:edit',     NULL, 2, 1, 1),
(123, 12, '角色删除', 3, NULL,           NULL, 'system:role:delete',   NULL, 3, 1, 1),
(131, 13, '菜单新增', 3, NULL,           NULL, 'system:menu:add',      NULL, 1, 1, 1),
(132, 13, '菜单修改', 3, NULL,           NULL, 'system:menu:edit',     NULL, 2, 1, 1),
(133, 13, '菜单删除', 3, NULL,           NULL, 'system:menu:delete',   NULL, 3, 1, 1),
(141, 14, '部门新增', 3, NULL,           NULL, 'system:dept:add',      NULL, 1, 1, 1),
(142, 14, '部门修改', 3, NULL,           NULL, 'system:dept:edit',     NULL, 2, 1, 1),
(143, 14, '部门删除', 3, NULL,           NULL, 'system:dept:delete',   NULL, 3, 1, 1),
(151, 15, '字典新增', 3, NULL,           NULL, 'system:dict:add',      NULL, 1, 1, 1),
(152, 15, '字典修改', 3, NULL,           NULL, 'system:dict:edit',     NULL, 2, 1, 1),
(153, 15, '字典删除', 3, NULL,           NULL, 'system:dict:delete',   NULL, 3, 1, 1);

-- 4. admin 绑定超级管理员
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- super_admin 全量菜单关联（代码层对该角色另有全量放行，此处保持数据完整性）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu`;

-- 5. 修正 admin 口令密文（admin / admin123，BCrypt）
UPDATE `sys_user`
SET `password` = '$2a$10$vzR.SQlLbfbmeypvVHqejOSVnFzzP0mXs5g5My7zxwZvREAZ7geuS'
WHERE `username` = 'admin';

-- 6. 内置字典
INSERT INTO `sys_dict_type` (`id`, `name`, `type`, `status`, `remark`) VALUES
(1, '通用状态', 'sys_common_status', 1, '启用/停用通用枚举'),
(2, '是否',     'sys_yes_no',        1, '布尔值展示枚举');

INSERT INTO `sys_dict_data` (`dict_type`, `label`, `value`, `sort`, `status`) VALUES
('sys_common_status', '启用', '1', 1, 1),
('sys_common_status', '停用', '0', 2, 1),
('sys_yes_no',        '是',   '1', 1, 1),
('sys_yes_no',        '否',   '0', 2, 1);
