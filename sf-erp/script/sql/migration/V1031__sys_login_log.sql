-- =====================================================================
-- V1031: 登录日志表（sys_login_log）
-- 说明：登录链路审计（登录成功/失败/锁定/登出四类事件），由
--       AuthServiceImpl 发布 Spring 事件 → LoginLogListener 异步解耦落库。
--       与 sys_oper_log（操作日志）互补：登录接口在安全白名单内、无 JWT
--       上下文，@OperLog 切面记录不到操作人/租户，故独立建表专门记录。
--       日志表只增不改，deleted 列按建表规范保留但恒为 0。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_login_log` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)   NOT NULL COMMENT '登录账号(登录失败时也有值)',
    `user_id`     BIGINT        DEFAULT NULL COMMENT '用户ID(账号可定位时有值,账号不存在为NULL)',
    `event`       VARCHAR(20)   NOT NULL COMMENT '事件:LOGIN登录 LOGOUT登出',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '结果:1成功 0失败',
    `error_msg`   VARCHAR(500)  DEFAULT NULL COMMENT '失败原因(密码错误/账号锁定/账号停用等,截断)',
    `ip`          VARCHAR(50)   DEFAULT NULL COMMENT '客户端IP',
    `user_agent`  VARCHAR(500)  DEFAULT NULL COMMENT '浏览器UA(截断到500字符)',
    `cost_ms`     BIGINT        DEFAULT NULL COMMENT '耗时(毫秒,登录含BCrypt校验)',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`      BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(共享日志,0)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '登录/登出时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '登录日志表';
