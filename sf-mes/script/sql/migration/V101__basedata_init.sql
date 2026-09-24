-- =====================================================================
-- V101: 基础资料模块初始表（erp-basedata）
-- 范围：计量单位 / 物料分类 / 物料 / 客户 / 供应商 / 仓库 / 库位 / 结算方式
-- 约定：
--   * 所有表统一带 id/create_by/create_time/update_by/update_time/deleted；
--   * 金额 DECIMAL(18,2)，数量 DECIMAL(18,4)，单位换算比率 DECIMAL(18,6)；
--   * 状态列一律 TINYINT：1-启用 0-停用（COMMENT 注明取值）；
--   * 编码唯一约束显式建 uk_ 索引（逻辑删除后编码不复用，历史行占位合法）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 计量单位 base_unit
--    换算规则：同 group_code 的单位为一组，组内换算 = 数量 * 源.rate / 目标.rate，
--    基准单位 is_base=1 且 rate=1。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_unit` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_code`  VARCHAR(30)   NOT NULL COMMENT '换算组编码,同组单位可互相换算',
    `name`        VARCHAR(30)   NOT NULL COMMENT '单位名称(如:个/箱/千克)',
    `rate`        DECIMAL(18,6) NOT NULL DEFAULT 1 COMMENT '换算比率(相对组内基准单位,基准=1)',
    `is_base`     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否基准单位:1是 0否',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_unit_group` (`group_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计量单位表';

-- ---------------------------------------------------------------------
-- 2. 物料分类 base_material_category（树形,parent_id=0 为根）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_material_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID,0=根节点',
    `code`        VARCHAR(30)  NOT NULL COMMENT '分类编码(唯一)',
    `name`        VARCHAR(50)  NOT NULL COMMENT '分类名称',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序号(越小越靠前)',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`code`),
    KEY `idx_category_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料分类表(树)';

-- ---------------------------------------------------------------------
-- 3. 物料 base_material
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_material` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`            VARCHAR(40)   NOT NULL COMMENT '物料编码(唯一,规则:WL+日期+3位流水)',
    `name`            VARCHAR(100)  NOT NULL COMMENT '物料名称',
    `spec`            VARCHAR(200)  DEFAULT NULL COMMENT '规格型号',
    `category_id`     BIGINT        DEFAULT NULL COMMENT '物料分类ID(base_material_category.id)',
    `unit_id`         BIGINT        DEFAULT NULL COMMENT '计量单位ID(base_unit.id)',
    `barcode`         VARCHAR(50)   DEFAULT NULL COMMENT '条码',
    `batch_managed`   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否批次管理:1是 0否',
    `serial_managed`  TINYINT       NOT NULL DEFAULT 0 COMMENT '是否序列号管理:1是 0否',
    `safety_stock`    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '安全库存(数量,DECIMAL(18,4))',
    `ref_cost`        DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '参考成本价(金额,DECIMAL(18,2))',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_material_code` (`code`),
    KEY `idx_material_name` (`name`),
    KEY `idx_material_category` (`category_id`),
    KEY `idx_material_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料表';

-- ---------------------------------------------------------------------
-- 4. 客户 base_customer
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_customer` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`             VARCHAR(40)   NOT NULL COMMENT '客户编码(唯一,规则:KH+日期+3位流水)',
    `name`             VARCHAR(100)  NOT NULL COMMENT '客户名称',
    `contact`          VARCHAR(50)   DEFAULT NULL COMMENT '联系人',
    `phone`            VARCHAR(20)   DEFAULT NULL COMMENT '手机号',
    `address`          VARCHAR(200)  DEFAULT NULL COMMENT '地址',
    `credit_code`      VARCHAR(30)   DEFAULT NULL COMMENT '统一社会信用代码',
    `settle_type_id`   BIGINT        DEFAULT NULL COMMENT '结算方式ID(base_settle_type.id)',
    `credit_limit`     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '信用额度(金额,DECIMAL(18,2))',
    `init_receivable`  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '期初应收(金额,DECIMAL(18,2))',
    `status`           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`           VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`        BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_code` (`code`),
    KEY `idx_customer_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户表';

-- ---------------------------------------------------------------------
-- 5. 供应商 base_supplier
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_supplier` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`            VARCHAR(40)   NOT NULL COMMENT '供应商编码(唯一,规则:GYS+日期+3位流水)',
    `name`            VARCHAR(100)  NOT NULL COMMENT '供应商名称',
    `contact`         VARCHAR(50)   DEFAULT NULL COMMENT '联系人',
    `phone`           VARCHAR(20)   DEFAULT NULL COMMENT '手机号',
    `address`         VARCHAR(200)  DEFAULT NULL COMMENT '地址',
    `credit_code`     VARCHAR(30)   DEFAULT NULL COMMENT '统一社会信用代码',
    `settle_type_id`  BIGINT        DEFAULT NULL COMMENT '结算方式ID(base_settle_type.id)',
    `init_payable`    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '期初应付(金额,DECIMAL(18,2))',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_code` (`code`),
    KEY `idx_supplier_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商表';

-- ---------------------------------------------------------------------
-- 6. 仓库 base_warehouse
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_warehouse` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(40)  NOT NULL COMMENT '仓库编码(唯一,规则:CK+日期+3位流水)',
    `name`        VARCHAR(100) NOT NULL COMMENT '仓库名称',
    `manager`     VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
    `address`     VARCHAR(200) DEFAULT NULL COMMENT '地址',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用:1是 0否',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '仓库表';

-- ---------------------------------------------------------------------
-- 7. 库位 base_location（仓库下多库位）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_location` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id` BIGINT        NOT NULL COMMENT '所属仓库ID(base_warehouse.id)',
    `code`         VARCHAR(40)   NOT NULL COMMENT '库位编码(仓库内唯一)',
    `name`         VARCHAR(50)   NOT NULL COMMENT '库位名称',
    `capacity`     DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '容量(数量,DECIMAL(18,4))',
    `status`       TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`       VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_location_wh_code` (`warehouse_id`, `code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库位表';

-- ---------------------------------------------------------------------
-- 8. 结算方式 base_settle_type
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `base_settle_type` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(20) NOT NULL COMMENT '方式编码(唯一)',
    `name`        VARCHAR(50) NOT NULL COMMENT '方式名称(现金/转账/承兑/月结等)',
    `days`        INT         NOT NULL DEFAULT 0 COMMENT '账期天数(月结30=30天)',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settle_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '结算方式表';

-- =====================================================================
-- 种子数据（演示与下拉初始化；Flyway 仅执行一次，可安全 INSERT）
-- =====================================================================

-- 结算方式内置：现金/转账/承兑/月结
INSERT INTO `base_settle_type` (`code`, `name`, `days`, `status`, `remark`) VALUES
('XJ', '现金', 0, 1, '款到发货'),
('ZZ', '转账', 0, 1, '银行转账'),
('CD', '承兑', 30, 1, '银行承兑汇票'),
('YJ', '月结', 30, 1, '月结30天');

-- 计量单位内置：数量组(个/打/箱) + 重量组(千克/吨)
INSERT INTO `base_unit` (`group_code`, `name`, `rate`, `is_base`, `status`) VALUES
('NUM', '个', 1, 1, 1),
('NUM', '打', 12, 0, 1),
('NUM', '箱', 10, 0, 1),
('WT',  '千克', 1, 1, 1),
('WT',  '吨', 1000, 0, 1);

-- 物料分类内置两层树：原材料/成品 及常用子类
INSERT INTO `base_material_category` (`parent_id`, `code`, `name`, `sort`, `status`) VALUES
(0, 'CL', '原材料', 1, 1),
(0, 'CP', '成品', 2, 1),
(0, 'BCP', '半成品', 3, 1);

-- 仓库内置：主仓/不良品仓
INSERT INTO `base_warehouse` (`code`, `name`, `manager`, `address`, `status`) VALUES
('CK-MAIN', '主仓库', '张仓管', '上海市嘉定区安亭镇ERP路1号', 1),
('CK-BAD',  '不良品仓', '李仓管', '上海市嘉定区安亭镇ERP路1号', 1);
