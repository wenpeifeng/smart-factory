-- =====================================================================
-- V701: 看板模块（kanban，版本段 V70x）—— 安灯呼叫
--   kb_andon 车间安灯异常呼叫（工作台/移动端发起，看板大屏展示，闭环处理）
-- =====================================================================

CREATE TABLE IF NOT EXISTS `kb_andon` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `call_no`       VARCHAR(40)  NOT NULL COMMENT '呼叫单号(AD+yyyyMMdd+4位流水)',
    `workcenter_id` BIGINT       DEFAULT NULL COMMENT '工作中心ID(base_workcenter.id)',
    `device_id`     BIGINT       DEFAULT NULL COMMENT '设备ID(eqp_device.id,设备类异常)',
    `type`          VARCHAR(10)  NOT NULL COMMENT '类型:DEVICE设备 MATERIAL物料 QUALITY质量 OTHER其他',
    `caller_name`   VARCHAR(50)  NOT NULL COMMENT '呼叫人',
    `message`       VARCHAR(500) NOT NULL COMMENT '异常描述',
    `status`        VARCHAR(10)  NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN待响应 RESPOND处理中 CLOSED已关闭',
    `call_time`     DATETIME     NOT NULL COMMENT '呼叫时间',
    `respond_time`  DATETIME     DEFAULT NULL COMMENT '响应时间',
    `responder`     VARCHAR(50)  DEFAULT NULL COMMENT '响应人',
    `close_time`    DATETIME     DEFAULT NULL COMMENT '关闭时间',
    `close_remark`  VARCHAR(500) DEFAULT NULL COMMENT '关闭说明',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID',
    `create_by`     BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_andon_no` (`call_no`),
    KEY `idx_andon_status` (`status`, `call_time`),
    KEY `idx_andon_workcenter` (`workcenter_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安灯异常呼叫';
