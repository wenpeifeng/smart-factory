-- =====================================================================
-- V212: 返工/报废单（execution 模块，版本段 V21x）
-- 说明：质检不合格品处置决定=返工/报废 时生成；返工单执行=重新过站原工序，
--       报废单执行=回写工单/工序报废数并计入 ERP 回传的完工汇报差异。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `exe_rework_scrap` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`       VARCHAR(40)   NOT NULL COMMENT '单号(FG返工/ BF报废 前缀+yyyyMMdd+4位流水)',
    `type`           VARCHAR(10)   NOT NULL COMMENT '类型:REWORK返工 SCRAP报废',
    `workorder_id`   BIGINT        NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `wo_no`          VARCHAR(30)   NOT NULL COMMENT '工单号(快照)',
    `wo_step_id`     BIGINT        DEFAULT NULL COMMENT '发生工序任务ID(plan_wo_step.id)',
    `operation_name` VARCHAR(100)  DEFAULT NULL COMMENT '工序名称(快照)',
    `sn`             VARCHAR(64)   DEFAULT NULL COMMENT '产品SN/批次(可空=按数量)',
    `qty`            DECIMAL(18,4) NOT NULL COMMENT '数量',
    `reason`         VARCHAR(500)  NOT NULL COMMENT '原因(引用质检不合格描述)',
    `qc_reject_id`   BIGINT        DEFAULT NULL COMMENT '关联不合格处置单ID(qc_reject.id)',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN待处理 DONE已处理 CLOSED已关闭',
    `handle_remark`  VARCHAR(500)  DEFAULT NULL COMMENT '处理说明',
    `handle_time`    DATETIME      DEFAULT NULL COMMENT '处理时间',
    `tenant_id`      BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`         BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`      BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_order_no` (`order_no`),
    KEY `idx_rs_workorder` (`workorder_id`),
    KEY `idx_rs_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '返工/报废单';
