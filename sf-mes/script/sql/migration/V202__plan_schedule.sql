-- =====================================================================
-- V202: 排程看板（plan 模块，版本段 V20x）
-- 内容：plan_schedule 工作中心甘特条目 —— 工单工序在工作中心上的时间片，
--       支持看板端手动拖拽调整（仅改 plan_start_time/plan_end_time，不做自动 APS）。
-- 约束：同一工作中心条目时间片允许重叠（人工排程判断），仅校验起止时间合法性。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `plan_schedule` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `wo_step_id`      BIGINT   NOT NULL COMMENT '工单工序任务ID(plan_wo_step.id)',
    `workorder_id`    BIGINT   NOT NULL COMMENT '工单ID(冗余,便于按单查询)',
    `workcenter_id`   BIGINT   NOT NULL COMMENT '工作中心ID(base_workcenter.id)',
    `plan_start_time` DATETIME NOT NULL COMMENT '计划开始时间(甘特起点,可拖拽调整)',
    `plan_end_time`   DATETIME NOT NULL COMMENT '计划结束时间(甘特终点,可拖拽调整)',
    `lock_flag`       TINYINT  NOT NULL DEFAULT 0 COMMENT '锁定标志:1锁定(看板不可拖动) 0可调',
    `tenant_id`       BIGINT   NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT   NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`       BIGINT   DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT   DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schedule_step` (`wo_step_id`),
    KEY `idx_schedule_wc_time` (`workcenter_id`, `plan_start_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '排程甘特条目(工作中心时间片,手动排程)';
