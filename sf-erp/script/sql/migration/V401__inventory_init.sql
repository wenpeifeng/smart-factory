-- =====================================================================
-- V401: 库存管理模块初始表
-- 范围：即时库存(inv_stock)、库存流水(inv_movement)、其他入库/出库单、
--       库存盘点单。
-- 设计要点：
--   1) inv_stock 是全系统唯一库存事实表，唯一键(仓库+物料+deleted)保证
--      一仓一料一行；qty 用 DECIMAL(18,4)；version 乐观锁辅助并发排查。
--   2) inv_movement 记录每一次库存变动（正入负出），是库存审计的唯一来源，
--      采购/销售/生产模块按约定表结构直接写入（后续升级为调 StockService）。
--   3) 库存表与 base_warehouse/base_material 之间不建物理外键（模块并行开发、
--      逻辑外键 + 索引），跨表名称由查询时 LEFT JOIN 补齐。
--   4) 所有表统一带 id/create_by/create_time/update_by/update_time/deleted。
-- =====================================================================

-- ---------- 1. 即时库存 ----------
CREATE TABLE IF NOT EXISTS `inv_stock` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id` BIGINT        NOT NULL COMMENT '仓库ID(逻辑外键 base_warehouse.id)',
    `location_id`  BIGINT        DEFAULT NULL COMMENT '库位ID(逻辑外键 base_location.id，预留)',
    `material_id`  BIGINT        NOT NULL COMMENT '物料ID(逻辑外键 base_material.id)',
    `qty`          DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '现存量(正数)',
    `version`      INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，每次变动+1',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stock` (`warehouse_id`, `material_id`, `deleted`),
    KEY `idx_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '即时库存表(仓库+物料维度)';

-- ---------- 2. 库存流水 ----------
CREATE TABLE IF NOT EXISTS `inv_movement` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `biz_type`     VARCHAR(20)   NOT NULL COMMENT '业务类型:PUR_IN采购入库/PUR_RET采购退货/SAL_OUT销售出库/SAL_RET销售退货/OTH_IN其他入库/OTH_OUT其他出库/ADJUST盘点调整/MOVE调拨/PROD_IN生产入库/PROD_USE生产领料',
    `biz_id`       BIGINT        DEFAULT NULL COMMENT '业务单据ID(如其他入库单id)',
    `biz_no`       VARCHAR(40)   DEFAULT NULL COMMENT '业务单号(如OI202609050001)',
    `warehouse_id` BIGINT        NOT NULL COMMENT '仓库ID',
    `material_id`  BIGINT        NOT NULL COMMENT '物料ID',
    `qty`          DECIMAL(18,4) NOT NULL COMMENT '变动数量(正数入库/负数出库)',
    `remark`       VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_mat_wh_time` (`material_id`, `warehouse_id`, `create_time`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_biz_no` (`biz_no`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存流水表(每一次库存变动一条，正入负出)';

-- ---------- 3. 其他入库单(头) ----------
CREATE TABLE IF NOT EXISTS `inv_other_in` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `no`          VARCHAR(40)  NOT NULL COMMENT '单号(OI+yyyyMMdd+4位流水)',
    `warehouse_id` BIGINT      NOT NULL COMMENT '入库仓库ID',
    `reason`      VARCHAR(100) DEFAULT NULL COMMENT '入库原因(领用退回/盘盈/赠品/样品/其他等)',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0草稿 1已审核(审核后过账库存)',
    `audit_time`  DATETIME     DEFAULT NULL COMMENT '审核时间',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_no` (`no`),
    KEY `idx_warehouse` (`warehouse_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '其他入库单(无来源的手工入库)';

-- ---------- 4. 其他入库单(明细行) ----------
CREATE TABLE IF NOT EXISTS `inv_other_in_line` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `other_in_id` BIGINT        NOT NULL COMMENT '头表ID(inv_other_in.id)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID',
    `qty`         DECIMAL(18,4) NOT NULL COMMENT '入库数量(正数)',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '行备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_other_in` (`other_in_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '其他入库单明细行';

-- ---------- 5. 其他出库单(头) ----------
CREATE TABLE IF NOT EXISTS `inv_other_out` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `no`          VARCHAR(40)  NOT NULL COMMENT '单号(OO+yyyyMMdd+4位流水)',
    `warehouse_id` BIGINT      NOT NULL COMMENT '出库仓库ID',
    `reason`      VARCHAR(100) DEFAULT NULL COMMENT '出库原因(报损/盘亏/领用/赠品/样品/其他等)',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0草稿 1已审核(审核后过账库存)',
    `audit_time`  DATETIME     DEFAULT NULL COMMENT '审核时间',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_no` (`no`),
    KEY `idx_warehouse` (`warehouse_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '其他出库单(无来源的手工出库)';

-- ---------- 6. 其他出库单(明细行) ----------
CREATE TABLE IF NOT EXISTS `inv_other_out_line` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `other_out_id` BIGINT        NOT NULL COMMENT '头表ID(inv_other_out.id)',
    `material_id`  BIGINT        NOT NULL COMMENT '物料ID',
    `qty`          DECIMAL(18,4) NOT NULL COMMENT '出库数量(正数)',
    `remark`       VARCHAR(200)  DEFAULT NULL COMMENT '行备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_other_out` (`other_out_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '其他出库单明细行';

-- ---------- 7. 库存盘点单(头) ----------
CREATE TABLE IF NOT EXISTS `inv_check` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `no`            VARCHAR(40)  NOT NULL COMMENT '单号(CK+yyyyMMdd+4位流水)',
    `warehouse_id`  BIGINT       NOT NULL COMMENT '盘点仓库ID',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0盘点中 1已完成(差异已生成ADJUST流水调平)',
    `complete_time` DATETIME     DEFAULT NULL COMMENT '完成(调平)时间',
    `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_no` (`no`),
    KEY `idx_warehouse` (`warehouse_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存盘点单(按仓库生成库存快照)';

-- ---------- 8. 库存盘点单(明细行=盘点时的库存快照) ----------
CREATE TABLE IF NOT EXISTS `inv_check_detail` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `check_id`    BIGINT        NOT NULL COMMENT '头表ID(inv_check.id)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID',
    `book_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '账面数量(生成快照时的库存)',
    `actual_qty`  DECIMAL(18,4) DEFAULT NULL COMMENT '实盘数量(录入前为NULL表示未盘点)',
    `diff_qty`    DECIMAL(18,4) DEFAULT NULL COMMENT '差异=实盘-账面(完成盘点时按此调平)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_check_material` (`check_id`, `material_id`, `deleted`),
    KEY `idx_check` (`check_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存盘点明细行(快照+实盘+差异)';
