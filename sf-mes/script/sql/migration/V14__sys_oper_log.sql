-- =====================================================================
-- V14: 操作日志表（sys_oper_log）
-- 说明：由 @OperLog 注解 + AOP 切面异步落库，记录写操作的人/事/参数/耗时/结果。
--       日志表只增不改，deleted 列按建表规范保留但恒为 0。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `module`         VARCHAR(50)   NOT NULL COMMENT '功能模块(注解 module 属性,如 用户管理)',
    `operation`      VARCHAR(50)   NOT NULL COMMENT '操作内容(注解 operation 属性,如 新增用户)',
    `method`         VARCHAR(200)  DEFAULT NULL COMMENT '执行方法(类#方法名)',
    `request_method` VARCHAR(10)   DEFAULT NULL COMMENT '请求方式(GET/POST/PUT/DELETE)',
    `request_url`    VARCHAR(255)  DEFAULT NULL COMMENT '请求地址',
    `request_params` VARCHAR(2000) DEFAULT NULL COMMENT '请求参数(JSON,超长截断)',
    `operator_id`    BIGINT        DEFAULT NULL COMMENT '操作人ID',
    `operator_name`  VARCHAR(30)   DEFAULT NULL COMMENT '操作人账号',
    `ip`             VARCHAR(50)   DEFAULT NULL COMMENT '客户端IP',
    `cost_ms`        BIGINT        DEFAULT NULL COMMENT '耗时(毫秒)',
    `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '结果:1成功 0失败',
    `error_msg`      VARCHAR(500)  DEFAULT NULL COMMENT '失败原因(异常信息,截断)',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_module` (`module`),
    KEY `idx_operator` (`operator_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '操作日志表';
