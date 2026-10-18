-- 认证中心员工生命周期镜像：用于子系统列表排序、筛选与集团变更通知。
ALTER TABLE `sys_user`
    ADD COLUMN `identity_status` TINYINT DEFAULT NULL COMMENT '认证中心人员状态:1在职 2离职 3全局停用' AFTER `identity_immutable`,
    ADD COLUMN `identity_inactive_reason` VARCHAR(30) DEFAULT NULL COMMENT '当前不可登录原因:LEFT/SUSPENDED/APP_DISABLED/REVOKED' AFTER `identity_status`,
    ADD COLUMN `entry_date` DATE DEFAULT NULL COMMENT '入职日期(认证中心镜像)' AFTER `identity_inactive_reason`,
    ADD COLUMN `leave_date` DATE DEFAULT NULL COMMENT '离职日期(认证中心镜像)' AFTER `entry_date`,
    ADD COLUMN `identity_create_time` DATETIME DEFAULT NULL COMMENT '集团账号建档时间' AFTER `leave_date`,
    ADD COLUMN `identity_update_time` DATETIME DEFAULT NULL COMMENT '集团人员最近操作时间' AFTER `identity_create_time`,
    ADD KEY `idx_identity_entry_status` (`identity_status`, `entry_date`);

UPDATE `sys_user`
SET `identity_status` = IF(`status` = 1, 1, 3),
    `identity_create_time` = `create_time`,
    `identity_update_time` = `update_time`
WHERE `auth_assigned` = 1 AND `identity_status` IS NULL;
