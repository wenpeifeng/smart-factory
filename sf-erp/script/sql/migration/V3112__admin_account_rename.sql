-- 管理员账号统一：admin/admin123 -> erp_admin/123456（套件级账号规范，见套件 README）
-- 说明：三系统独立库，账号本不冲突；统一命名为 erp_admin/mes_admin/admin 便于记忆与审计。
UPDATE sys_user SET username = 'erp_admin', password = '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm' WHERE id = 1 AND username = 'admin';
