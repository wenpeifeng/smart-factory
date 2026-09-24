-- =====================================================================
-- V1: 系统基座初始表（sys_user 骨架版）
-- 说明：P0 认证/组织/菜单等完整表结构在后续 V 版本脚本中补充。
-- 约定：所有表统一带 id/create_by/create_time/update_by/update_time/deleted
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(30)  NOT NULL COMMENT '登录账号(唯一)',
    `nickname`    VARCHAR(30)  NOT NULL COMMENT '显示姓名',
    `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt口令密文',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(50)  DEFAULT NULL COMMENT '邮箱',
    `dept_id`     BIGINT       DEFAULT NULL COMMENT '部门ID',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

-- 初始管理员：admin / admin123（BCrypt 密文，可用 /api 中登录后强制改密）
INSERT INTO `sys_user` (`username`, `nickname`, `password`, `status`, `remark`)
VALUES ('admin', '系统管理员',
        '$2a$10$mE.qmcV0mFU5NcKh73TZx.z4ueI/.bDWbj0c1S1vB6cGQmF2XkZmG',
        1, '内置超管，首次登录后请修改密码');
