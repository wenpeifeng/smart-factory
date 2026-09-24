-- =====================================================================
-- V601: 生产制造模块(erp-mrp)初始表
-- 范围：BOM 物料清单 / 主产计划 MPS / MRP 运算(批次+展开明细+发补建议)
--       / 生产订单 / 生产领料 / 委外加工
-- 约定：所有表统一带 id/create_by/create_time/update_by/update_time/deleted；
--       数量 DECIMAL(18,4)，BOM 用量/损耗计算过程 DECIMAL(18,6)，金额 DECIMAL(18,2)。
-- 跨模块约定表（base_material / base_warehouse / base_supplier / inv_stock /
-- inv_movement / pur_order / pur_order_detail / fin_payable）由 basedata/
-- inventory/purchase/finance 模块各自迁移脚本创建，本脚本只建 mrp_ 前缀表。
-- =====================================================================

-- 1. BOM 物料清单头（成品 + 版本）
CREATE TABLE IF NOT EXISTS `mrp_bom` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id` BIGINT       NOT NULL COMMENT '成品物料ID(base_material.id)',
    `version`     VARCHAR(20)  NOT NULL COMMENT 'BOM版本号(如 V1.0)',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 ACTIVE已审核(有效) DISABLED停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM物料清单头';

-- 2. BOM 子件明细
CREATE TABLE IF NOT EXISTS `mrp_bom_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `bom_id`      BIGINT        NOT NULL COMMENT 'BOM头ID(mrp_bom.id)',
    `material_id` BIGINT        NOT NULL COMMENT '子件物料ID(base_material.id)',
    `qty`         DECIMAL(18,6) NOT NULL COMMENT '单位用量(单件成品耗用子件数量)',
    `loss_rate`   DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '损耗率%(0-100)',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_bom` (`bom_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM子件明细';

-- 3. 主生产计划 MPS
CREATE TABLE IF NOT EXISTS `mrp_mps` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id` BIGINT        NOT NULL COMMENT '成品物料ID(base_material.id)',
    `qty`         DECIMAL(18,4) NOT NULL COMMENT '计划生产数量',
    `start_date`  DATE          NOT NULL COMMENT '计划开工日期',
    `status`      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待运算 CALCULATED已运算',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_material` (`material_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '主生产计划MPS';

-- 4. MRP 运算批次（一次运算一行）
CREATE TABLE IF NOT EXISTS `mrp_calc` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_no`         VARCHAR(30) NOT NULL COMMENT '运算批次号(MR+yyyyMMdd+4位流水)',
    `mps_count`        INT         NOT NULL DEFAULT 0 COMMENT '本次运算MPS行数',
    `suggestion_count` INT         NOT NULL DEFAULT 0 COMMENT '本次产出的发补建议条数',
    `remark`           VARCHAR(200) DEFAULT NULL COMMENT '运算备注',
    `create_by`        BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`      DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(运算时间)',
    `update_by`        BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MRP运算批次';

-- 5. MRP 运算展开明细（全树，供批次追溯/层级查看）
CREATE TABLE IF NOT EXISTS `mrp_calc_detail` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_no`           VARCHAR(30)   NOT NULL COMMENT '运算批次号(mrp_calc.batch_no)',
    `mps_id`             BIGINT        NOT NULL COMMENT '来源MPS行ID(多成品各自成树)',
    `level`              INT           NOT NULL DEFAULT 0 COMMENT 'BOM层级:0=成品 1=一级子件...',
    `material_id`        BIGINT        NOT NULL COMMENT '物料ID',
    `gross_qty`          DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '毛需求',
    `stock_qty`          DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '运算时现有库存快照(inv_stock汇总)',
    `onway_qty`          DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '运算时在途数量快照(采购在途)',
    `net_qty`            DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '净需求=毛需求-可用(库存+在途)扣抵',
    `suggest_qty`        DECIMAL(18,6) DEFAULT NULL COMMENT '建议数量(无建议为NULL)',
    `suggest_date`       DATE          DEFAULT NULL COMMENT '建议日期=需求日期-提前期(7天)',
    `plan_type`          VARCHAR(10)   DEFAULT NULL COMMENT '建议类型:PURCHASE采购 PRODUCE生产(无建议为NULL)',
    `node_path`          VARCHAR(500)  NOT NULL COMMENT '树节点路径(根到当前物料ID串,防环+前端组树)',
    `parent_path`        VARCHAR(500)  DEFAULT NULL COMMENT '父节点路径(根节点为NULL)',
    `create_by`          BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_batch` (`batch_no`),
    KEY `idx_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MRP运算展开明细';

-- 6. MRP 发补建议（运算结果，可转生产订单）
CREATE TABLE IF NOT EXISTS `mrp_suggestion` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_no`     VARCHAR(30)   NOT NULL COMMENT '运算批次号(mrp_calc.batch_no)',
    `plan_type`    VARCHAR(10)   NOT NULL COMMENT '建议类型:PURCHASE采购建议 PRODUCE生产建议',
    `material_id`  BIGINT        NOT NULL COMMENT '物料ID',
    `qty`          DECIMAL(18,4) NOT NULL COMMENT '建议数量(净需求按物料汇总)',
    `suggest_date` DATE          NOT NULL COMMENT '建议日期=需求日期-提前期(7天)',
    `level`        INT           NOT NULL DEFAULT 0 COMMENT 'BOM层级(取该物料首次出现的层级)',
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待处理 CONVERTED已转单',
    `ref_type`     VARCHAR(20)   DEFAULT NULL COMMENT '转单引用类型:WORK_ORDER生产订单',
    `ref_id`       BIGINT        DEFAULT NULL COMMENT '转单引用ID',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_batch` (`batch_no`),
    KEY `idx_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MRP发补建议';

-- 7. 生产订单
CREATE TABLE IF NOT EXISTS `mrp_work_order` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`        VARCHAR(30)   NOT NULL COMMENT '单号(MO+yyyyMMdd+4位流水)',
    `material_id`     BIGINT        NOT NULL COMMENT '成品物料ID',
    `qty`             DECIMAL(18,4) NOT NULL COMMENT '计划生产数量',
    `bom_id`          BIGINT        NOT NULL COMMENT 'BOMID(下达时校验仍为有效)',
    `bom_version`     VARCHAR(20)   NOT NULL COMMENT 'BOM版本号快照',
    `warehouse_id`    BIGINT        DEFAULT NULL COMMENT '默认入库仓库(base_warehouse.id)',
    `plan_start_date` DATE          DEFAULT NULL COMMENT '计划开始日期',
    `plan_end_date`   DATE          DEFAULT NULL COMMENT '计划结束日期',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 RELEASED已下达 PRODUCING生产中 FINISHED已完工 CLOSED已关闭',
    `finish_qty`      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计完工良品数量',
    `bad_qty`         DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计不良品数量',
    `source_type`     VARCHAR(20)   DEFAULT NULL COMMENT '来源:MANUAL手工 MRP_SUGGESTIONMRP建议转单',
    `source_id`       BIGINT        DEFAULT NULL COMMENT '来源单据ID',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_status` (`status`),
    KEY `idx_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产订单';

-- 8. 生产领料单头
CREATE TABLE IF NOT EXISTS `mrp_material_issue` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `issue_no`      VARCHAR(30) NOT NULL COMMENT '单号(MI+yyyyMMdd+4位流水)',
    `work_order_id` BIGINT      NOT NULL COMMENT '生产订单ID(mrp_work_order.id)',
    `warehouse_id`  BIGINT      NOT NULL COMMENT '发料仓库(base_warehouse.id)',
    `status`        VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 AUDITED已审核(已扣库存)',
    `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_issue_no` (`issue_no`),
    KEY `idx_work_order` (`work_order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产领料单头';

-- 9. 生产领料明细
CREATE TABLE IF NOT EXISTS `mrp_material_issue_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `issue_id`    BIGINT        NOT NULL COMMENT '领料单ID(mrp_material_issue.id)',
    `material_id` BIGINT        NOT NULL COMMENT '子件物料ID',
    `should_qty`  DECIMAL(18,6) NOT NULL COMMENT '应领数量(BOM用量x订单数量x(1+损耗率))',
    `actual_qty`  DECIMAL(18,6) NOT NULL COMMENT '实领数量',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_issue` (`issue_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产领料明细';

-- 10. 委外加工单（简化：发料 + 加工费 + 完工入库）
CREATE TABLE IF NOT EXISTS `mrp_outsource` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`        VARCHAR(30)   NOT NULL COMMENT '单号(OS+yyyyMMdd+4位流水)',
    `supplier_id`     BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `material_id`     BIGINT        NOT NULL COMMENT '加工后成品物料ID',
    `qty`             DECIMAL(18,4) NOT NULL COMMENT '委外加工数量',
    `bom_id`          BIGINT        NOT NULL COMMENT 'BOMID(发料子件依据)',
    `bom_version`     VARCHAR(20)   NOT NULL COMMENT 'BOM版本号快照',
    `issue_warehouse_id`   BIGINT   DEFAULT NULL COMMENT '发料仓库',
    `in_warehouse_id`      BIGINT   DEFAULT NULL COMMENT '完工入库仓库',
    `fee_unit_price`  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '加工费单价(元/件)',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 ISSUED已发料 RECEIVED已完工入库',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_supplier` (`supplier_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '委外加工单';

-- 11. 委外发料明细
CREATE TABLE IF NOT EXISTS `mrp_outsource_item` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `outsource_id` BIGINT        NOT NULL COMMENT '委外单ID(mrp_outsource.id)',
    `material_id`  BIGINT        NOT NULL COMMENT '发料子件物料ID',
    `qty`          DECIMAL(18,6) NOT NULL COMMENT '发料数量(BOM用量x委外数量x(1+损耗率))',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_outsource` (`outsource_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '委外发料明细';
