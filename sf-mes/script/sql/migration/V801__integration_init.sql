-- =====================================================
-- V801 integration 模块初始化：ERP 回传推送日志表
-- 目的：MES -> ERP 回传（完工报工/领退料/质检结果）的幂等控制、
--       失败重试与对账补偿的数据载体。每次回传先落 PENDING 记录，
--       成功置 SUCCESS，失败置 FAILED 并由定时任务重试（上限 5 次）。
--       对账 = 按 biz_type+biz_no 查询推送状态列表。
-- =====================================================
CREATE TABLE IF NOT EXISTS `int_push_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `biz_type`      VARCHAR(20)  NOT NULL COMMENT '业务类型：WORK_REPORT完工报工/PICKING领料/RETURN退料/QUALITY质检结果',
    `biz_id`        BIGINT       NOT NULL COMMENT '业务单据id',
    `biz_no`        VARCHAR(40)  NOT NULL COMMENT '业务单据号（幂等键之一）',
    `payload`       JSON         NOT NULL COMMENT '回传报文快照',
    `status`        VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SUCCESS/FAILED',
    `retry_count`   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retry`     INT          NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    `last_error`    VARCHAR(1000)        DEFAULT NULL COMMENT '最近一次失败原因',
    `push_time`     DATETIME              DEFAULT NULL COMMENT '最近一次推送时间',
    `success_time`  DATETIME              DEFAULT NULL COMMENT '成功时间',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户id',
    `org_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '组织id',
    `create_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT                DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME              DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_push_biz` (`biz_type`, `biz_id`, `tenant_id`),
    KEY `idx_push_status` (`status`, `retry_count`),
    KEY `idx_push_no` (`biz_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ERP回传推送日志（幂等+重试+对账）';
