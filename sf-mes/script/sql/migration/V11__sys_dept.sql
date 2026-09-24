-- =====================================================================
-- V11: 部门表（sys_dept）
-- 说明：组织架构，parent_id 自关联构成树（0 表示根节点）。
--       用户表 sys_user.dept_id 引用本表（逻辑外键，不建物理约束，
--       便于部门停用/调整时不清空历史用户归属）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级部门ID(0=根节点)',
    `name`        VARCHAR(50)  NOT NULL COMMENT '部门名称',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序(越小越靠前)',
    `leader`      VARCHAR(30)  DEFAULT NULL COMMENT '负责人',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门表';
