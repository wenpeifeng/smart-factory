-- =====================================================================
-- V421: WMS 库内作业深化（一）—— 库区/库位三级 + 作业任务 + 波次 + ABC 分级 + 权限种子
-- 范围（全部幂等：information_schema 判断列/表/索引存在后再执行，重复执行无害）：
--   1) base_zone 新表：仓库→库区→库位三级中的"库区"层
--      （zone_type: RECEIVE 收货 / STORAGE 存储 / PICKING 拣选 / SHIP 发货 / DEFECT 不良品）；
--      种子：每个存量仓库各类型一个库区（编码 Z<仓编码后缀>-<类型>）。
--   2) base_location + zone_id BIGINT NULL + loc_type VARCHAR(20) DEFAULT 'STORAGE'
--      （存量库位默认归 STORAGE 语义，zone_id 空表示未分区，前端树归"未分区"组）。
--   3) inv_stock + location_id BIGINT NOT NULL DEFAULT 0
--      ★ 口径（与 docs/modules/inventory.md WMS 章节一致）：
--        location_id = 0 表示"汇总/未上架行"（V401 预留的 NULL 列迁移为 0 哨兵，
--        保证唯一键 uk_stock 对未上架行仍然强约束——MySQL 唯一键含 NULL 时不防重）；
--        location_id > 0 表示"库位行"= 真实库存行（仅批次物料落库位行，非批次物料恒 0）。
--        唯一键 uk_stock(warehouse_id, material_id, batch_no, deleted)
--          → uk_stock(warehouse_id, material_id, batch_no, location_id, deleted)。
--   4) inv_movement + location_id BIGINT NOT NULL DEFAULT 0（业务过账落流水时带实际扣减/入库库位）。
--   5) inv_check + source VARCHAR(20) DEFAULT 'MANUAL'（CYCLE=ABC 循环盘点自动生成）。
--   6) inv_check_detail + location_id BIGINT NOT NULL DEFAULT 0
--      + 唯一键 uk_check_material 升维含 location_id（批次+库位行快照每行一条）。
--   7) wms_task 作业任务表 / wms_wave 波次表 / wms_abc_class 物料 ABC 分级表。
--   8) sys_menu 种子：作业任务/波次管理/序列号/ABC 分级 菜单与按钮权限码
--      inventory:wms:{task,wave,serial,abc}:{list,edit,audit}，挂库存管理目录(230)。
-- =====================================================================

-- ---------- 1. base_zone 库区表 ----------
CREATE TABLE IF NOT EXISTS `base_zone` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(0=集团共享)',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `warehouse_id` BIGINT      NOT NULL COMMENT '所属仓库ID(base_warehouse.id)',
    `code`        VARCHAR(40)  NOT NULL COMMENT '库区编码(仓库内唯一)',
    `name`        VARCHAR(50)  NOT NULL COMMENT '库区名称',
    `zone_type`   VARCHAR(20)  NOT NULL DEFAULT 'STORAGE' COMMENT '库区类型:RECEIVE收货 STORAGE存储 PICKING拣选 SHIP发货 DEFECT不良品',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_zone_wh_code` (`warehouse_id`, `code`),
    KEY `idx_zone_type` (`zone_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库区(仓库→库区→库位三级中间层)';

-- ---------- 2. base_location: 库区归属 + 库位用途类型 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_location' AND COLUMN_NAME = 'zone_id') = 0,
    'ALTER TABLE base_location ADD COLUMN zone_id BIGINT NULL COMMENT ''所属库区ID(base_zone.id,空=未分区)'' AFTER warehouse_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_location' AND COLUMN_NAME = 'loc_type') = 0,
    'ALTER TABLE base_location ADD COLUMN loc_type VARCHAR(20) NOT NULL DEFAULT ''STORAGE'' COMMENT ''库位用途:STORAGE存储 PICKING拣选 RECEIVE收货 SHIP发货 DEFECT不良品'' AFTER zone_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 种子库区：每仓各类型一个库区（幂等：仓库+编码不存在才插） ----------
INSERT INTO `base_zone` (`org_id`, `tenant_id`, `warehouse_id`, `code`, `name`, `zone_type`, `create_by`, `update_by`)
SELECT 0, 1000, w.id,
       CONCAT('Z-', zt.code), zt.name, zt.code, 0, 0
FROM base_warehouse w
         CROSS JOIN (SELECT 'RECEIVE' AS code, '收货区' AS name UNION ALL
                     SELECT 'STORAGE', '存储区' UNION ALL
                     SELECT 'PICKING', '拣选区' UNION ALL
                     SELECT 'SHIP', '发货区' UNION ALL
                     SELECT 'DEFECT', '不良品区') zt
WHERE w.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM base_zone z
                  WHERE z.warehouse_id = w.id AND z.code = CONCAT('Z-', zt.code) AND z.deleted = 0);

-- ---------- 4. inv_stock: 库位列（0=汇总/未上架行，>0=库位行） ----------
-- 4.1 列不存在则新增（NOT NULL DEFAULT 0）
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'location_id') = 0,
    'ALTER TABLE inv_stock ADD COLUMN location_id BIGINT NOT NULL DEFAULT 0 COMMENT ''库位ID(0=汇总/未上架行,>0=库位行base_location.id,仅批次物料)'' AFTER warehouse_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.2 V401 预留的可空列迁移为 0 哨兵（NULL → 0 + 改 NOT NULL），保证唯一键防重语义
SET @ddl = IF(
    (SELECT IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'location_id') = 'YES',
    'UPDATE inv_stock SET location_id = 0 WHERE location_id IS NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND COLUMN_NAME = 'location_id') = 'YES',
    'ALTER TABLE inv_stock MODIFY COLUMN location_id BIGINT NOT NULL DEFAULT 0 COMMENT ''库位ID(0=汇总/未上架行,>0=库位行base_location.id,仅批次物料)''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.3 唯一键升维（加 location_id）：仅当现 uk_stock 仍是 V411 四列结构时执行
SET @ddl = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock' AND INDEX_NAME = 'uk_stock')
        = 'warehouse_id,material_id,batch_no,deleted',
    'ALTER TABLE inv_stock DROP INDEX uk_stock, ADD UNIQUE KEY uk_stock (warehouse_id, material_id, batch_no, location_id, deleted)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5. inv_movement: 库位列（0=未定位/历史流水） ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_movement' AND COLUMN_NAME = 'location_id') = 0,
    'ALTER TABLE inv_movement ADD COLUMN location_id BIGINT NOT NULL DEFAULT 0 COMMENT ''库位ID(0=未定位/汇总行,>0=实际作业库位)'' AFTER warehouse_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 6. inv_check: 盘点来源（MANUAL 手工 / CYCLE ABC 循环盘点自动生成） ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check' AND COLUMN_NAME = 'source') = 0,
    'ALTER TABLE inv_check ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT ''MANUAL'' COMMENT ''来源:MANUAL手工 CYCLE循环盘点自动'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 7. inv_check_detail: 库位列 + 唯一键升维 ----------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check_detail' AND COLUMN_NAME = 'location_id') = 0,
    'ALTER TABLE inv_check_detail ADD COLUMN location_id BIGINT NOT NULL DEFAULT 0 COMMENT ''库位ID(快照自inv_stock.location_id,0=未上架行)'' AFTER material_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_check_detail' AND INDEX_NAME = 'uk_check_material')
        = 'check_id,material_id,batch_no,deleted',
    'ALTER TABLE inv_check_detail DROP INDEX uk_check_material, ADD UNIQUE KEY uk_check_material (check_id, material_id, batch_no, location_id, deleted)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 8. wms_task 库内作业任务表 ----------
CREATE TABLE IF NOT EXISTS `wms_task` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`            BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=总厂)',
    `tenant_id`         BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `task_no`           VARCHAR(40)   NOT NULL COMMENT '任务号(T+yyyyMMdd+4位流水)',
    `task_type`         VARCHAR(10)   NOT NULL COMMENT '任务类型:PUTAWAY上架 PICK拣选 MOVE移库 REPLENISH补货',
    `warehouse_id`      BIGINT        NOT NULL COMMENT '仓库ID',
    `material_id`       BIGINT        NOT NULL COMMENT '物料ID',
    `batch_no`          VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '批次号(非批次物料空串;PICK来源库位行时=该行批次)',
    `qty`               DECIMAL(18,4) NOT NULL COMMENT '任务数量',
    `location_id`       BIGINT        DEFAULT NULL COMMENT '目标/来源库位ID(PUTAWAY/REPLENISH=目标,PICK=来源)',
    `from_location_id`  BIGINT        DEFAULT NULL COMMENT '来源库位ID(MOVE/REPLENISH=调出库位)',
    `wave_id`           BIGINT        DEFAULT NULL COMMENT '所属波次ID(wms_wave.id,空=独立任务)',
    `source_type`       VARCHAR(20)   NOT NULL DEFAULT '' COMMENT '来源单据类型(PUR_IN/SAL_RET/OTH_IN/PROD_IN/SAL_OUT/OTH_OUT,空=手工)',
    `source_id`         BIGINT        NOT NULL DEFAULT 0 COMMENT '来源单据ID(0=手工)',
    `source_no`         VARCHAR(40)   DEFAULT NULL COMMENT '来源单号',
    `assignee`          BIGINT        DEFAULT NULL COMMENT '执行人用户ID(NULL=待分配)',
    `status`            TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0待分配 10待执行 20执行中 30完成 40取消',
    `recommend_reason`  VARCHAR(200)  DEFAULT NULL COMMENT '推荐库位理由(同物料现有库位/存储区空容库位等)',
    `finish_time`       DATETIME      DEFAULT NULL COMMENT '完成时间',
    `remark`            VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`         BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_task_status` (`status`),
    KEY `idx_task_wh_status` (`warehouse_id`, `status`),
    KEY `idx_task_wave` (`wave_id`),
    KEY `idx_task_source` (`source_type`, `source_id`),
    KEY `idx_task_assignee` (`assignee`, `status`),
    KEY `idx_task_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WMS库内作业任务(上架/拣选/移库/补货)';

-- ---------- 9. wms_wave 波次表 ----------
CREATE TABLE IF NOT EXISTS `wms_wave` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`        BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=总厂)',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `wave_no`       VARCHAR(40)  NOT NULL COMMENT '波次号(W+yyyyMMdd+4位流水)',
    `warehouse_id`  BIGINT       NOT NULL COMMENT '仓库ID',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0建波 10拣选中 20完成',
    `finish_time`   DATETIME     DEFAULT NULL COMMENT '完成时间(全部任务完成时写)',
    `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wave_no` (`wave_no`),
    KEY `idx_wave_status` (`status`),
    KEY `idx_wave_wh` (`warehouse_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WMS拣选波次(多张出库单合并拣选)';

-- ---------- 10. wms_abc_class 物料 ABC 分级表 ----------
CREATE TABLE IF NOT EXISTS `wms_abc_class` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`      BIGINT      NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=总厂)',
    `tenant_id`   BIGINT      NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `material_id` BIGINT      NOT NULL COMMENT '物料ID',
    `level`       VARCHAR(1)  NOT NULL COMMENT '分级:A(高频前20%) B(中频30%) C(低频50%)',
    `freq`        BIGINT      NOT NULL DEFAULT 0 COMMENT '近90天出库频次(出库流水行数)',
    `calc_date`   DATE        NOT NULL COMMENT '分级计算日期',
    `create_by`   BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_abc_material` (`material_id`),
    KEY `idx_abc_level` (`level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料ABC分级(循环盘点依据,近90天出库频次)';

-- ---------- 11. sys_menu 种子 ----------
-- 注意：菜单种子已迁移至 V1061__wms_menu_seed.sql。
-- 原因：sys_menu 的 org_id/tenant_id 列由 V1012 添加，本脚本版本号(421)在全新库中
-- 先于 V1012 执行会导致 Unknown column 'org_id'。种子移到 V1061(>1012) 保证任何库全量迁移可跑。
