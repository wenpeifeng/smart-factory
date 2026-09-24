-- =====================================================================
-- V21: sys_user 增加独立工号列（身份键与登录名解耦，与 ERP/auth 同构）
-- 背景：此前同步把工号灌进 username 列。工号是不可变身份匹配键，登录名是
--       可变登录凭据，两个概念解耦：employee_no 唯一承载对账匹配，
--       username 保留为登录名（同步用户仍=工号，行为不变）。
-- 存量回填：auth-center 同步来源用户（remark 标记）username 即工号。
-- =====================================================================

ALTER TABLE `sys_user`
    ADD COLUMN `employee_no` VARCHAR(40) DEFAULT NULL COMMENT '工号(auth-center身份键,不可变,同步/JIT/SSO匹配键)' AFTER `username`;

-- 存量回填：同步来源用户工号=登录名；本地降级账号(admin/演示账号)无工号留空
UPDATE `sys_user` SET `employee_no` = `username` WHERE `remark` LIKE 'auth-center%';

ALTER TABLE `sys_user`
    ADD UNIQUE KEY `uk_employee_no` (`employee_no`);
