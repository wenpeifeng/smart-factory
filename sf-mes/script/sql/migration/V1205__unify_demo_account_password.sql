-- MES 本地应急账号与岗位演示账号口令统一为 123456。
-- 认证中心同步用户（E300x）仍使用随机占位口令，只能通过 OIDC 登录。
UPDATE sys_user
SET password = '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm',
    update_by = 1,
    update_time = CURRENT_TIMESTAMP
WHERE tenant_id = 1000
  AND username IN ('mes_admin', 'operator', 'qc', 'wh')
  AND deleted = 0;
