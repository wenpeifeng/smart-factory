-- =====================================================================
-- V401: 物料执行模块（material，版本段 V40x）
-- 内容：
--   1) mat_picking / mat_picking_item    车间领料单（头/行）
--   2) mat_return / mat_return_item      车间退料单（头/行）
--   3) mat_shop_stock                    线边仓库存（车间内存量，批次粒度）
--   4) mat_batch_sn                      批次/SN 档案（收货/产出登记，追溯主档）
--   5) mat_label_log                     标签打印记录
-- 边界：ERP 管库存账（仓库/账面），MES 管线边仓与批次/SN 执行明细；
--       领料=ERP 出库下推或 MES 发起回传，退料同理（integration 回传）。
-- =====================================================================

-- 1. 领料单头
CREATE TABLE IF NOT EXISTS `mat_picking` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pick_no`       VARCHAR(30)   NOT NULL COMMENT '领料单号(LL+yyyyMMdd+4位流水)',
    `workorder_id`  BIGINT        NOT NULL COMMENT '工单ID(plan_workorder.id)',
    `wo_no`         VARCHAR(30)   NOT NULL COMMENT '工单号(快照)',
    `warehouse_id`  BIGINT        DEFAULT NULL COMMENT '发料仓库(base_warehouse.id,ERP仓库)',
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 AUDITED已审核(生效) CANCELLED已取消',
    `audit_time`    DATETIME      DEFAULT NULL COMMENT '审核时间',
    `auditor`       VARCHAR(50)   DEFAULT NULL COMMENT '审核人',
    `pick_type`     VARCHAR(20)   NOT NULL DEFAULT 'NORMAL' COMMENT '类型:NORMAL正常领料 SUPPLEMENT补料',
    `remark`        VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `erp_pushed`    TINYINT       NOT NULL DEFAULT 0 COMMENT 'ERP回传标志:1已回传 0未(审核后异步回传)',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pick_no` (`pick_no`),
    KEY `idx_pick_workorder` (`workorder_id`),
    KEY `idx_pick_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '车间领料单头';

-- 2. 领料单行
CREATE TABLE IF NOT EXISTS `mat_picking_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `picking_id`  BIGINT        NOT NULL COMMENT '领料单ID(mat_picking.id)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `batch_no`    VARCHAR(64)   DEFAULT NULL COMMENT '批次号(扫码选批,空=批次不管理)',
    `qty`         DECIMAL(18,4) NOT NULL COMMENT '领料数量',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_pi_picking` (`picking_id`),
    KEY `idx_pi_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '车间领料单行';

-- 3. 退料单头
CREATE TABLE IF NOT EXISTS `mat_return` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `return_no`     VARCHAR(30)   NOT NULL COMMENT '退料单号(TL+yyyyMMdd+4位流水)',
    `workorder_id`  BIGINT        DEFAULT NULL COMMENT '工单ID(plan_workorder.id,余料退库)',
    `wo_no`         VARCHAR(30)   DEFAULT NULL COMMENT '工单号(快照)',
    `return_type`   VARCHAR(20)   NOT NULL DEFAULT 'LEFTOVER' COMMENT '类型:LEFTOVER余料退库 REJECTED不良退库',
    `warehouse_id`  BIGINT        DEFAULT NULL COMMENT '退入仓库(base_warehouse.id)',
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 AUDITED已审核 CANCELLED已取消',
    `audit_time`    DATETIME      DEFAULT NULL COMMENT '审核时间',
    `auditor`       VARCHAR(50)   DEFAULT NULL COMMENT '审核人',
    `remark`        VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `erp_pushed`    TINYINT       NOT NULL DEFAULT 0 COMMENT 'ERP回传标志:1已回传 0未',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_return_no` (`return_no`),
    KEY `idx_return_workorder` (`workorder_id`),
    KEY `idx_return_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '车间退料单头';

-- 4. 退料单行
CREATE TABLE IF NOT EXISTS `mat_return_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `return_id`   BIGINT        NOT NULL COMMENT '退料单ID(mat_return.id)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `batch_no`    VARCHAR(64)   DEFAULT NULL COMMENT '批次号',
    `qty`         DECIMAL(18,4) NOT NULL COMMENT '退料数量',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_ri_return` (`return_id`),
    KEY `idx_ri_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '车间退料单行';

-- 5. 线边仓库存（批次粒度；条件 UPDATE 增减防超发）
CREATE TABLE IF NOT EXISTS `mat_shop_stock` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id`   BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `batch_no`      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '批次号(无批次管理物料用空串)',
    `workcenter_id` BIGINT        DEFAULT NULL COMMENT '线边库位所属工作中心(空=车间总库位)',
    `qty`           DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '现存量',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shop_stock` (`material_id`, `batch_no`, `workcenter_id`, `org_id`),
    KEY `idx_shop_stock_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '线边仓库存(批次粒度)';

-- 6. 批次/SN 档案（来料批次由 ERP 收货同步，产出批次/SN 由过站产出登记；追溯主档）
CREATE TABLE IF NOT EXISTS `mat_batch_sn` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `obj_type`      VARCHAR(10)  NOT NULL COMMENT '对象类型:BATCH批次 SN单品序列号',
    `obj_no`        VARCHAR(64)  NOT NULL COMMENT '批次号/SN',
    `material_id`   BIGINT       NOT NULL COMMENT '物料ID(base_material.id)',
    `source_type`   VARCHAR(20)  NOT NULL COMMENT '来源:ERP_RECEIPT ERP收货同步 PRODUCE MES产出 REWORK返工',
    `source_no`     VARCHAR(40)  DEFAULT NULL COMMENT '来源单号(ERP收货单/工单号)',
    `workorder_id`  BIGINT       DEFAULT NULL COMMENT '产出工单ID(自产)',
    `qty`           DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '数量(SN恒为1,批次为批次量)',
    `supplier_id`   BIGINT       DEFAULT NULL COMMENT '供应商ID(来料批次)',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'IN_STOCK' COMMENT '状态:IN_STOCK在库 CONSUMED已耗用 SHIPPED已出货',
    `parent_sn`     VARCHAR(64)  DEFAULT NULL COMMENT '父SN(关键件装配关系,构成BOM级追溯)',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_sn` (`obj_type`, `obj_no`),
    KEY `idx_bs_material` (`material_id`),
    KEY `idx_bs_workorder` (`workorder_id`),
    KEY `idx_bs_parent` (`parent_sn`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '批次/SN档案(追溯主档)';

-- 7. 标签打印记录
CREATE TABLE IF NOT EXISTS `mat_label_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `obj_type`    VARCHAR(20)  NOT NULL COMMENT '标签对象:MATERIAL物料 BATCH批次 SN序列号 WO工单',
    `obj_id`      BIGINT       DEFAULT NULL COMMENT '对象ID',
    `obj_no`      VARCHAR(64)  NOT NULL COMMENT '对象编号(物料码/批次号/SN/工单号)',
    `template`    VARCHAR(50)  DEFAULT NULL COMMENT '标签模板名',
    `copies`      INT          NOT NULL DEFAULT 1 COMMENT '打印份数',
    `printer`     VARCHAR(50)  DEFAULT NULL COMMENT '打印机',
    `print_time`  DATETIME     NOT NULL COMMENT '打印时间',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_label_obj` (`obj_type`, `obj_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '标签打印记录';
