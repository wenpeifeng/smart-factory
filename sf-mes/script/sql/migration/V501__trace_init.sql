-- =====================================================================
-- V501: 追溯模块（trace，版本段 V50x）
-- 内容：
--   1) trace_link        追溯关系（上料批次/SN -> 产品批次/SN x 工序 的消耗关系）
--   2) trace_ebr         电子批记录快照（工单完工时生成，聚合工艺/物料/质检/过站全数据）
-- 说明：
--   * 正向追溯：批次/SN -> 用到了哪里（下游产品）；反向追溯：产品 -> 用了哪些来料批次；
--     数据源 = 领料批次(mat_picking_item.batch_no) + 过站记录(exe_station_record)
--     + 关键件SN(exe_station_record.key_component_sn，写入时同时展开为 trace_link 行)；
--   * trace_ebr 为完工汇总快照（JSON 载荷），供合规审查导出，不可修改。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `trace_link` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_no`     VARCHAR(64) NOT NULL COMMENT '产品批次号/SN(产出对象)',
    `workorder_id`   BIGINT      NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `wo_step_id`     BIGINT      DEFAULT NULL COMMENT '工序任务ID(消耗发生工序)',
    `component_no`   VARCHAR(64) NOT NULL COMMENT '消耗对象编号(来料批次/关键件SN)',
    `component_type` VARCHAR(10) NOT NULL COMMENT '消耗对象类型:BATCH批次 SN序列号',
    `material_id`    BIGINT      DEFAULT NULL COMMENT '消耗物料ID(快照)',
    `qty`            DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '消耗数量(SN类为1)',
    `link_time`      DATETIME    NOT NULL COMMENT '关联时间(过站/领料时间)',
    `tenant_id`      BIGINT      NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`         BIGINT      NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`      BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_trace_product` (`product_no`),
    KEY `idx_trace_component` (`component_no`),
    KEY `idx_trace_workorder` (`workorder_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '追溯关系(消耗->产出)';

CREATE TABLE IF NOT EXISTS `trace_ebr` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ebr_no`        VARCHAR(40) NOT NULL COMMENT '批记录编号(EBR+yyyyMMdd+4位流水)',
    `workorder_id`  BIGINT      NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `wo_no`         VARCHAR(30) NOT NULL COMMENT '工单号(快照)',
    `material_id`   BIGINT      NOT NULL COMMENT '成品物料ID',
    `batch_no`      VARCHAR(64) DEFAULT NULL COMMENT '产出批次号',
    `content`       JSON        NOT NULL COMMENT '批记录内容JSON(工艺路线/过站明细/用料批次/质检结果/人员设备)',
    `generate_time` DATETIME    NOT NULL COMMENT '生成时间',
    `tenant_id`     BIGINT      NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT      NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ebr_no` (`ebr_no`),
    UNIQUE KEY `uk_ebr_workorder` (`workorder_id`),
    KEY `idx_ebr_batch` (`batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '电子批记录快照(完工生成,只读)';
