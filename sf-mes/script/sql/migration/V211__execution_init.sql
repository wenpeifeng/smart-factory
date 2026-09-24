-- =====================================================================
-- V211: 执行管理模块（execution，版本段 V21x）
-- 内容：
--   1) exe_station_record  过站扫码记录（一次过站一行：工单/工序/工作中心/SN/人员/数量/工时）
--   2) exe_wip             在制品汇总（工单 x 工序 粒度，过站回写）
--   3) exe_rework_scrap    返工/报废单（不良品处置落地执行）
-- 说明：
--   * 过站模型（借鉴 ktg-mes/MES-SpringBoot）：上工序产出 SN/批次 → 扫码过本站 →
--     记录过站明细并回写 plan_wo_step 累计数与状态；末道工序完工回传 ERP（integration）；
--   * exe_wip 为汇总表（冗余可重算），用于看板/报表高频查询。
-- =====================================================================

-- 1. 过站扫码记录
CREATE TABLE IF NOT EXISTS `exe_station_record` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `record_no`       VARCHAR(40)   NOT NULL COMMENT '过站流水号(GZ+yyyyMMdd+4位流水)',
    `workorder_id`    BIGINT        NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `wo_no`           VARCHAR(30)   NOT NULL COMMENT '工单号(快照)',
    `wo_step_id`      BIGINT        NOT NULL COMMENT '工序任务ID(plan_wo_step.id)',
    `seq_no`          INT           NOT NULL COMMENT '工序序号(快照)',
    `operation_id`    BIGINT        NOT NULL COMMENT '工序ID(快照)',
    `operation_name`  VARCHAR(100)  NOT NULL COMMENT '工序名称(快照)',
    `workcenter_id`   BIGINT        DEFAULT NULL COMMENT '工作中心ID(快照)',
    `device_id`       BIGINT        DEFAULT NULL COMMENT '设备ID(eqp_device.id,选填)',
    `sn`              VARCHAR(64)   DEFAULT NULL COMMENT '产品SN/批次号(扫入)',
    `key_component_sn` VARCHAR(500) DEFAULT NULL COMMENT '关键件SN清单(逗号分隔,关键工序采集)',
    `good_qty`        DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '合格数量',
    `scrap_qty`       DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '报废数量',
    `rework_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '返工数量',
    `work_minutes`    INT           NOT NULL DEFAULT 0 COMMENT '实际工时(分钟)',
    `worker_id`       BIGINT        DEFAULT NULL COMMENT '作业人员用户ID(sys_user.id)',
    `worker_name`     VARCHAR(50)   NOT NULL COMMENT '作业人员姓名',
    `team_id`         BIGINT        DEFAULT NULL COMMENT '班组ID(base_team.id)',
    `station_time`    DATETIME      NOT NULL COMMENT '过站时间',
    `result`          VARCHAR(10)   NOT NULL DEFAULT 'PASS' COMMENT '过站结果:PASS合格 FAIL不合格',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_station_record_no` (`record_no`),
    KEY `idx_sr_workorder` (`workorder_id`),
    KEY `idx_sr_step` (`wo_step_id`),
    KEY `idx_sr_sn` (`sn`),
    KEY `idx_sr_time` (`station_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '过站扫码记录';

-- 2. 在制品汇总（工单 x 工序）
CREATE TABLE IF NOT EXISTS `exe_wip` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workorder_id`    BIGINT        NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `wo_no`           VARCHAR(30)   NOT NULL COMMENT '工单号(快照)',
    `wo_step_id`      BIGINT        NOT NULL COMMENT '工序任务ID(plan_wo_step.id)',
    `seq_no`          INT           NOT NULL COMMENT '工序序号',
    `operation_name`  VARCHAR(100)  NOT NULL COMMENT '工序名称(快照)',
    `workcenter_id`   BIGINT        DEFAULT NULL COMMENT '工作中心ID',
    `input_qty`       DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计投入数量(进入本工序)',
    `output_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计产出数量(合格过站)',
    `wip_qty`         DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '在制数量(=投入-产出-报废)',
    `scrap_qty`       DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计报废',
    `rework_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计返工',
    `last_station_time` DATETIME    DEFAULT NULL COMMENT '最近过站时间',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wip_step` (`wo_step_id`),
    KEY `idx_wip_workorder` (`workorder_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '在制品汇总(工单x工序)';
