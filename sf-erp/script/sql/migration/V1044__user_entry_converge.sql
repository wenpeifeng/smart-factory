-- =====================================================================
-- V1044: 用户管理入口收敛（身份体系移交 auth-center，与 MES V20 同款口径）
-- 移除本系统用户生命周期按钮权限（新增/删除/重置密码）：
-- 建号/改密/离职只在认证中心操作；本系统保留
--   查询(system:user:list) / 分配角色·组织·数据范围(system:user:edit) /
--   同步与应用级启停(/auth/sso/*，权限复用 system:user:edit)。
-- 用户档案更新入口统一收敛为 PUT /system/users/{id}/scope（数据范围/部门/组织）。
-- =====================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (
    SELECT `id` FROM `sys_menu` WHERE `perms` IN ('system:user:add', 'system:user:delete', 'system:user:resetPwd')
);
DELETE FROM `sys_menu` WHERE `perms` IN ('system:user:add', 'system:user:delete', 'system:user:resetPwd');
