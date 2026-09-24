-- =====================================================================
-- V611: 生产制造二期 —— 工艺路线 / 派工报工 / 能力计划(CRP简版)
-- 范围：工作中心 mrp_work_center / 工序定义 mrp_operation /
--       工艺路线 mrp_routing(+mrp_routing_step) /
--       工单工序任务 mrp_wo_step（生产订单下达时按已审核工艺路线顺排生成）/
--       工序报工明细 mrp_work_report
-- 约定：沿用 V601 基线 —— 每表带 id/create_by/create_time/update_by/
--       update_time/deleted；数量 DECIMAL(18,4)，工时(分钟) DECIMAL(8,2)。
-- QC 挂点：mrp_work_report.qc_note 与 mrp_wo_step.qc_pending_note 为
--       质量模块并行开发预留的"质量待处理说明"字段（见 docs/modules/mrp.md 二期章节）。
-- =====================================================================

-- 12. 工作中心（车间产能负荷的计量单元）
CREATE TABLE IF NOT EXISTS `mrp_work_center` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                  VARCHAR(40)  NOT NULL COMMENT '编码(WC+yyyyMMdd+4位流水)',
    `name`                  VARCHAR(100) NOT NULL COMMENT '工作中心名称',
    `workshop`              VARCHAR(100) DEFAULT NULL COMMENT '所属车间(文本)',
    `capacity_hours_per_day` DECIMAL(8,2) NOT NULL DEFAULT 8 COMMENT '每日产能工时(小时/天,CRP可用工时基数)',
    `equipment_count`       INT          NOT NULL DEFAULT 1 COMMENT '设备数',
    `status`                VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED启用 DISABLED停用',
    `remark`                VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`             BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wc_code` (`code`),
    KEY `idx_wc_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作中心';

-- 13. 工序定义（标准工序库，供工艺路线步骤引用）
CREATE TABLE IF NOT EXISTS `mrp_operation` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                  VARCHAR(40) NOT NULL COMMENT '编码(OP+yyyyMMdd+4位流水)',
    `name`                  VARCHAR(100) NOT NULL COMMENT '工序名称',
    `default_work_center_id` BIGINT     DEFAULT NULL COMMENT '默认工作中心ID(mrp_work_center.id)',
    `setup_minutes`         INT         NOT NULL DEFAULT 0 COMMENT '准备时间(分钟/批)',
    `unit_minutes`          DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '单位加工时间(分钟/每件)',
    `outsource_flag`        TINYINT     NOT NULL DEFAULT 0 COMMENT '外协标志:1外协 0自制',
    `status`                VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED启用 DISABLED停用',
    `remark`                VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`             BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`           DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`           DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_op_code` (`code`),
    KEY `idx_op_status` (`status`),
    KEY `idx_op_work_center` (`default_work_center_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工序定义';

-- 14. 工艺路线头（成品物料 + 版本；下达生产订单时取最新 ACTIVE 版本生成工序任务）
CREATE TABLE IF NOT EXISTS `mrp_routing` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id` BIGINT      NOT NULL COMMENT '成品物料ID(base_material.id)',
    `version`     VARCHAR(20) NOT NULL COMMENT '工艺路线版本号(如 V1.0)',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 ACTIVE已审核(有效) DISABLED停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_routing_material_version` (`material_id`, `version`),
    KEY `idx_routing_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工艺路线头';

-- 15. 工艺路线工序步骤（工序序列；工作中心/单件工时可覆盖工序默认值）
CREATE TABLE IF NOT EXISTS `mrp_routing_step` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `routing_id`    BIGINT      NOT NULL COMMENT '工艺路线ID(mrp_routing.id)',
    `seq_no`        INT         NOT NULL COMMENT '序号(工序顺序,从1起)',
    `operation_id`  BIGINT      NOT NULL COMMENT '工序ID(mrp_operation.id)',
    `work_center_id` BIGINT     DEFAULT NULL COMMENT '工作中心ID(空=取工序默认工作中心)',
    `unit_minutes`  DECIMAL(8,2) DEFAULT NULL COMMENT '单件工时分钟(空=取工序单位加工时间)',
    `report_flag`   TINYINT     NOT NULL DEFAULT 1 COMMENT '是否报工点:1是 0否',
    `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_rs_routing` (`routing_id`),
    KEY `idx_rs_operation` (`operation_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工艺路线工序步骤';

-- 16. 工单工序任务（生产订单下达时按工艺路线顺排生成；派工/报工的对象）
CREATE TABLE IF NOT EXISTS `mrp_wo_step` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `work_order_id`   BIGINT        NOT NULL COMMENT '生产订单ID(mrp_work_order.id)',
    `seq_no`          INT           NOT NULL COMMENT '序号(工序顺序)',
    `operation_id`    BIGINT        NOT NULL COMMENT '工序ID(mrp_operation.id)',
    `work_center_id`  BIGINT        DEFAULT NULL COMMENT '工作中心ID(mrp_work_center.id)',
    `unit_minutes`    DECIMAL(8,2)  NOT NULL DEFAULT 0 COMMENT '单件工时分钟(工艺路线快照)',
    `plan_qty`        DECIMAL(18,4) NOT NULL COMMENT '计划数量(=订单数量)',
    `plan_start_time` DATETIME      DEFAULT NULL COMMENT '计划开始时间(前置工序结束顺排)',
    `plan_end_time`   DATETIME      DEFAULT NULL COMMENT '计划结束时间(=计划开始+单件工时x数量)',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'PENDING_DISPATCH' COMMENT '状态:PENDING_DISPATCH待派工 DISPATCHED已派工 PROCESSING加工中 FINISHED已完工',
    `assignee`        VARCHAR(50)   DEFAULT NULL COMMENT '派工对象(员工姓名或设备)',
    `dispatch_time`   DATETIME      DEFAULT NULL COMMENT '派工时间',
    `finish_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计报工合格数',
    `bad_qty`         DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计报工不合格数',
    `actual_minutes`  INT           NOT NULL DEFAULT 0 COMMENT '累计实际工时(分钟)',
    `qc_pending_note` VARCHAR(500)  DEFAULT NULL COMMENT '质量待处理说明(不合格累计记录,QC模块并行开发挂点)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_ws_work_order` (`work_order_id`),
    KEY `idx_ws_status` (`status`),
    KEY `idx_ws_work_center` (`work_center_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单工序任务';

-- 17. 工序报工明细（一次报工一行；回写 mrp_wo_step 累计数与状态）
CREATE TABLE IF NOT EXISTS `mrp_work_report` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `wo_step_id`    BIGINT        NOT NULL COMMENT '工单工序任务ID(mrp_wo_step.id)',
    `work_order_id` BIGINT        NOT NULL COMMENT '生产订单ID(冗余,便于按订单追溯)',
    `operation_id`  BIGINT        NOT NULL COMMENT '工序ID(冗余)',
    `employee`      VARCHAR(50)   NOT NULL COMMENT '报工员工姓名',
    `good_qty`      DECIMAL(18,4) NOT NULL COMMENT '合格数',
    `bad_qty`       DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '不合格数',
    `work_minutes`  INT           NOT NULL DEFAULT 0 COMMENT '实际工时(分钟)',
    `qc_note`       VARCHAR(500)  DEFAULT NULL COMMENT '质量待处理说明(不合格时记录,QC模块并行开发挂点)',
    `report_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '报工时间',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_wr_wo_step` (`wo_step_id`),
    KEY `idx_wr_work_order` (`work_order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工序报工明细';
