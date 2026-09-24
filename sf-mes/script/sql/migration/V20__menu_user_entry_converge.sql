-- =====================================================================
-- V20: 用户管理入口收敛（身份体系移交 auth-center）
-- 移除 MES 端用户生命周期按钮权限（新增/删除/重置密码）：
-- 建号/改密/离职在认证中心操作；MES 保留 分配角色(system:user:edit) 与
-- 同步/应用级启停（integration:authsync:run，V17 已种）。
-- =====================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (
    SELECT `id` FROM `sys_menu` WHERE `perms` IN ('system:user:add', 'system:user:delete', 'system:user:resetPwd')
);
DELETE FROM `sys_menu` WHERE `perms` IN ('system:user:add', 'system:user:delete', 'system:user:resetPwd');
