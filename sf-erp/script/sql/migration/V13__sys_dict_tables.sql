-- =====================================================================
-- V13: 数据字典表（sys_dict_type / sys_dict_data）
-- 说明：字典类型（如 sys_common_status）下挂多条字典项（label/value）。
--       关联字段使用类型编码 type 字符串而非 ID，其他模块可直接按
--       GET /dict/{type} 拉取下拉选项，避免多一次 ID 换算。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(50)  NOT NULL COMMENT '字典名称(如 通用状态)',
    `type`        VARCHAR(100) NOT NULL COMMENT '类型编码(唯一,如 sys_common_status)',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type` (`type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典类型表';

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type`   VARCHAR(100) NOT NULL COMMENT '所属字典类型编码(sys_dict_type.type)',
    `label`       VARCHAR(50)  NOT NULL COMMENT '选项标签(展示用)',
    `value`       VARCHAR(50)  NOT NULL COMMENT '选项值(存储用)',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序(越小越靠前)',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_dict_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典数据表';
