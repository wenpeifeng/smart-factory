-- 管理员账号统一：admin/admin123 -> mes_admin/123456（套件级账号规范，见套件 README）
UPDATE sys_user SET username = 'mes_admin', password = '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm' WHERE id = 1 AND username = 'admin';
