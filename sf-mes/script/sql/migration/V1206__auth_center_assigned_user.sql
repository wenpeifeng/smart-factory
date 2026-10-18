-- 认证中心应用分配镜像：只有当前仍分配到 MES 的人员才出现在 MES 用户管理。
ALTER TABLE `sys_user`
    ADD COLUMN `auth_assigned` TINYINT NOT NULL DEFAULT 0
        COMMENT '认证中心当前已分配到MES:1是 0否/本地应急账号'
        AFTER `employee_no`,
    ADD COLUMN `identity_source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        COMMENT '认证中心身份来源:MANUAL/SEED'
        AFTER `auth_assigned`,
    ADD COLUMN `identity_immutable` TINYINT NOT NULL DEFAULT 0
        COMMENT '认证中心主档是否只读'
        AFTER `identity_source`,
    ADD COLUMN `position` VARCHAR(64) NULL COMMENT '岗位（认证中心镜像）'
        AFTER `nickname`;

-- 已通过同步或 JIT 建档的存量人员回填；本地应急/旧演示账号保持 0。
UPDATE `sys_user`
SET `auth_assigned` = 1
WHERE `employee_no` IS NOT NULL
  AND `remark` LIKE 'auth-center%'
  AND `deleted` = 0;
