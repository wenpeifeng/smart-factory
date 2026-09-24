-- =====================================================================
-- V201: 计划管理模块（plan，版本段 V20x）
-- 内容：
--   1) plan_workorder        生产工单（ERP 生产订单下推 / 手工创建）
--   2) plan_wo_material      工单用料需求（齐套分析对象）
--   3) plan_wo_step          工单工序任务（下达时按 ACTIVE 工艺路线生成）
--   4) plan_kit_check        齐套分析快照（头=一次分析，行=物料齐套结果）
-- 状态机（工单）：DRAFT草稿 → RELEASED已下达 → PRODUCING生产中 → FINISHED已完工 → CLOSED已关闭
-- 状态机（工序）：PENDING_DISPATCH待派工 → DISPATCHED已派工 → PROCESSING加工中 → FINISHED已完工
-- =====================================================================

-- 1. 生产工单
CREATE TABLE IF NOT EXISTS `plan_workorder` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `wo_no`           VARCHAR(30)   NOT NULL COMMENT '工单号(MO+yyyyMMdd+4位流水)',
    `material_id`     BIGINT        NOT NULL COMMENT '成品物料ID(base_material.id)',
    `qty`             DECIMAL(18,4) NOT NULL COMMENT '计划生产数量',
    `bom_id`          BIGINT        NOT NULL COMMENT 'BOMID(下达时校验仍为有效)',
    `bom_version`     VARCHAR(20)   NOT NULL COMMENT 'BOM版本号快照',
    `routing_id`      BIGINT        DEFAULT NULL COMMENT '工艺路线ID(下达时快照,空=无工序单工序报工)',
    `warehouse_id`    BIGINT        DEFAULT NULL COMMENT '完工入库仓库(base_warehouse.id,回传ERP用)',
    `plan_start_date` DATE          DEFAULT NULL COMMENT '计划开始日期',
    `plan_end_date`   DATE          DEFAULT NULL COMMENT '计划结束日期',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 RELEASED已下达 PRODUCING生产中 FINISHED已完工 CLOSED已关闭',
    `finish_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计完工良品数量',
    `scrap_qty`       DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计报废数量',
    `sn_mode`         VARCHAR(10)   NOT NULL DEFAULT 'BATCH' COMMENT '追踪模式:BATCH批次 SN单品序列号',
    `source_type`     VARCHAR(20)   DEFAULT NULL COMMENT '来源:ERP_ORDER ERP生产订单下推 MANUAL手工',
    `source_no`       VARCHAR(40)   DEFAULT NULL COMMENT '来源单号(ERP生产订单号)',
    `source_id`       BIGINT        DEFAULT NULL COMMENT '来源单据ID(ERP侧)',
    `close_remark`    VARCHAR(200)  DEFAULT NULL COMMENT '结案说明',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wo_no` (`wo_no`),
    KEY `idx_wo_status` (`status`),
    KEY `idx_wo_material` (`material_id`),
    KEY `idx_wo_source` (`source_type`, `source_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产工单';

-- 2. 工单用料需求（下达时按 BOM 展开；齐套分析按此表逐行比对可用量）
CREATE TABLE IF NOT EXISTS `plan_wo_material` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workorder_id` BIGINT       NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `material_id` BIGINT        NOT NULL COMMENT '子件物料ID(base_material.id)',
    `req_qty`     DECIMAL(18,6) NOT NULL COMMENT '需求数量(=工单数量x单位用量x(1+损耗率))',
    `issued_qty`  DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '已领料数量(领料单审核回写)',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_wom_workorder` (`workorder_id`),
    KEY `idx_wom_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单用料需求';

-- 3. 工单工序任务（工单下达时按工艺路线顺排生成；派工/过站报工的对象）
CREATE TABLE IF NOT EXISTS `plan_wo_step` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workorder_id`    BIGINT        NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `seq_no`          INT           NOT NULL COMMENT '序号(工序顺序)',
    `operation_id`    BIGINT        NOT NULL COMMENT '工序ID(base_operation.id)',
    `operation_name`  VARCHAR(100)  NOT NULL COMMENT '工序名称(快照)',
    `workcenter_id`   BIGINT        DEFAULT NULL COMMENT '工作中心ID(base_workcenter.id)',
    `unit_minutes`    DECIMAL(8,2)  NOT NULL DEFAULT 0 COMMENT '单件工时分钟(工艺路线快照)',
    `setup_minutes`   INT           NOT NULL DEFAULT 0 COMMENT '准备时间分钟(快照)',
    `plan_qty`        DECIMAL(18,4) NOT NULL COMMENT '计划数量(=工单数量)',
    `plan_start_time` DATETIME      DEFAULT NULL COMMENT '计划开始时间',
    `plan_end_time`   DATETIME      DEFAULT NULL COMMENT '计划结束时间',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'PENDING_DISPATCH' COMMENT '状态:PENDING_DISPATCH待派工 DISPATCHED已派工 PROCESSING加工中 FINISHED已完工',
    `assignee_id`     BIGINT        DEFAULT NULL COMMENT '派工对象用户ID(sys_user.id)',
    `assignee_name`   VARCHAR(50)   DEFAULT NULL COMMENT '派工对象姓名(快照)',
    `dispatch_time`   DATETIME      DEFAULT NULL COMMENT '派工时间',
    `finish_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计过站合格数',
    `scrap_qty`       DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计报废数',
    `rework_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计返工数',
    `actual_minutes`  INT           NOT NULL DEFAULT 0 COMMENT '累计实际工时(分钟)',
    `qc_flag`         TINYINT       NOT NULL DEFAULT 0 COMMENT '是否质检点(IPQC挂点):1是 0否',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_wos_workorder` (`workorder_id`),
    KEY `idx_wos_status` (`status`),
    KEY `idx_wos_workcenter` (`workcenter_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单工序任务';

-- 4. 齐套分析快照（头=一次分析，行=逐料结果；可用量来源=线边仓库存+ERP可用量(inegration查询)）
CREATE TABLE IF NOT EXISTS `plan_kit_check` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `check_no`      VARCHAR(30) NOT NULL COMMENT '分析单号(QT+yyyyMMdd+4位流水)',
    `workorder_id`  BIGINT      NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `wo_no`         VARCHAR(30) NOT NULL COMMENT '工单号(快照)',
    `result`        VARCHAR(20) NOT NULL COMMENT '分析结果:FULL齐套 SHORT缺料 PART部分齐套',
    `short_count`   INT         NOT NULL DEFAULT 0 COMMENT '缺料行数',
    `tenant_id`     BIGINT      NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT      NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kit_check_no` (`check_no`),
    KEY `idx_kit_workorder` (`workorder_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '齐套分析快照头';

CREATE TABLE IF NOT EXISTS `plan_kit_check_item` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `check_id`      BIGINT        NOT NULL COMMENT '齐套分析ID(plan_kit_check.id)',
    `material_id`   BIGINT        NOT NULL COMMENT '物料ID',
    `req_qty`       DECIMAL(18,6) NOT NULL COMMENT '需求数量',
    `available_qty` DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '可用数量(线边仓+ERP可用)',
    `short_qty`     DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '缺口数量(>0为缺料)',
    `result`        VARCHAR(10)   NOT NULL COMMENT '行结果:OK齐套 SHORT缺料',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_kci_check` (`check_id`),
    KEY `idx_kci_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '齐套分析快照明细';
