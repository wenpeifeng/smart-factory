-- =====================================================================
-- V12: RBAC 权限模型表（角色/菜单/用户角色/角色菜单）
-- 说明：菜单为三级树 —— type: 1目录 2菜单 3按钮；
--       菜单节点的 path 存完整路由（如 system/users，不带斜杠），
--       component 存前端组件路径（相对 pages/，如 system/users/UserList），
--       按钮节点只存 perms 权限标识（如 system:user:add）。
--       关系表（user_role/role_menu）按规范保留审计列，重分配时物理删除旧关联。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(30)  NOT NULL COMMENT '角色名称',
    `code`        VARCHAR(50)  NOT NULL COMMENT '角色编码(唯一,super_admin 为内置超管)',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表';

CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级菜单ID(0=根节点)',
    `name`        VARCHAR(50)  NOT NULL COMMENT '菜单/按钮名称',
    `type`        TINYINT      NOT NULL COMMENT '类型:1目录 2菜单 3按钮',
    `path`        VARCHAR(200) DEFAULT NULL COMMENT '路由地址(完整路径,不带斜杠,如 system/users)',
    `component`   VARCHAR(200) DEFAULT NULL COMMENT '前端组件路径(相对pages/,如 system/users/UserList)',
    `perms`       VARCHAR(100) DEFAULT NULL COMMENT '权限标识(按钮级,如 system:user:add)',
    `icon`        VARCHAR(50)  DEFAULT NULL COMMENT '菜单图标(AntD 图标名)',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序(越小越靠前)',
    `visible`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示:1显示 0隐藏',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '菜单权限表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `role_id`     BIGINT   NOT NULL COMMENT '角色ID',
    `create_by`   BIGINT   DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT   DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`     BIGINT   NOT NULL COMMENT '角色ID',
    `menu_id`     BIGINT   NOT NULL COMMENT '菜单ID',
    `create_by`   BIGINT   DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT   DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色-菜单关联表';
