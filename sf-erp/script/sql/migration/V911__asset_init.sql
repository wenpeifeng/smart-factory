-- =====================================================================
-- V911: 固定资产模块初始化（erp-asset）
-- 目的：
--   1) 建立固定资产 全套表结构（4 张表：类别 / 卡片 / 折旧期间 / 折旧明细）
--   2) 补充固定资产清理科目（1606，处置业务需要；幂等：已存在则跳过）
--   3) 预置资产类别种子（含科目映射：资产/累计折旧/折旧费用 → fin_subject，
--      按科目编码解析真实 ID，不硬编码主键）
-- 协作约定（遵守 docs/modules/finance.md 第 6 节）：
--   本模块按约定表结构直接读写 fin_subject / fin_voucher / fin_voucher_entry /
--   sys_dept / base_supplier（只读名称），不 import erp-finance / erp-system 代码。
--   凭证号与 erp-finance 共用同一 Redis 序列（erp:no:PZ:yyyyMMdd），保证全局唯一。
-- 金额规范：全部 DECIMAL(18,2)，代码层 BigDecimal + HALF_UP，禁止 float/double。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 补充会计科目：1606 固定资产清理（处置凭证借贷方科目；幂等插入）
-- ---------------------------------------------------------------------
INSERT INTO `fin_subject`
    (`code`, `name`, `parent_id`, `category`, `direction`, `status`, `remark`,
     `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT '1606', '固定资产清理', 0, 'ASSET', 'DEBIT', 1, '固定资产模块处置业务使用(V911预置)',
       0, NOW(), 0, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `fin_subject` WHERE `code` = '1606');

-- ---------------------------------------------------------------------
-- 1. 资产类别表（折旧政策 + 科目映射）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ast_category` (
    `id`                                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                                VARCHAR(20)   NOT NULL COMMENT '类别编码(唯一)',
    `name`                                VARCHAR(60)   NOT NULL COMMENT '类别名称(如:电子设备/运输设备/机器设备)',
    `depreciation_method`                 VARCHAR(20)   NOT NULL DEFAULT 'AVG' COMMENT '折旧方法:AVG平均年限法(WORKLOAD工作量法暂未实现)',
    `expected_life_months`                INT           NOT NULL COMMENT '预计使用年限(月)',
    `residual_rate`                       DECIMAL(6,2)  NOT NULL DEFAULT 0.00 COMMENT '残值率(%),如5.00表示5%',
    `asset_subject_id`                    BIGINT        NOT NULL COMMENT '资产科目ID(fin_subject,入账借方)',
    `accumulated_depreciation_subject_id` BIGINT        NOT NULL COMMENT '累计折旧科目ID(fin_subject,折旧贷方)',
    `depreciation_expense_subject_id`     BIGINT        NOT NULL COMMENT '折旧费用科目ID(fin_subject,折旧借方)',
    `remark`                              VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`                           BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`                         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                           BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`                         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                             TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_asset_subject` (`asset_subject_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资产类别表(折旧政策+科目映射)';

-- ---------------------------------------------------------------------
-- 2. 资产卡片表（净值 = 原值 - 累计折旧 为数据库生成列）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ast_asset` (
    `id`                     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `asset_code`             VARCHAR(40)   NOT NULL COMMENT '资产编码(唯一,ZC+4位流水自动生成)',
    `name`                   VARCHAR(100)  NOT NULL COMMENT '资产名称',
    `category_id`            BIGINT        NOT NULL COMMENT '类别ID(ast_category.id)',
    `spec`                   VARCHAR(200)  DEFAULT NULL COMMENT '规格型号',
    `supplier_id`            BIGINT        DEFAULT NULL COMMENT '供应商ID(base_supplier.id,可空)',
    `location`               VARCHAR(100)  DEFAULT NULL COMMENT '存放地点',
    `dept_id`                BIGINT        DEFAULT NULL COMMENT '使用部门ID(sys_dept.id,可空)',
    `keeper`                 VARCHAR(50)   DEFAULT NULL COMMENT '责任人',
    `original_value`         DECIMAL(18,2) NOT NULL COMMENT '原值',
    `residual_value`         DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '残值',
    `expected_life_months`   INT           NOT NULL COMMENT '预计使用年限(月)',
    `depreciated_months`     INT           NOT NULL DEFAULT 0 COMMENT '已提折旧月数',
    `accumulated_depreciation` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '累计折旧',
    `net_value`              DECIMAL(18,2) GENERATED ALWAYS AS (`original_value` - `accumulated_depreciation`) STORED COMMENT '净值(生成列=原值-累计折旧)',
    `account_date`           DATE          NOT NULL COMMENT '入账日期',
    `status`                 VARCHAR(20)   NOT NULL DEFAULT 'IN_USE' COMMENT '状态:IN_USE在用 IDLE闲置 DISPOSED处置',
    `entry_voucher_id`       BIGINT        DEFAULT NULL COMMENT '入账凭证ID(fin_voucher.id,入账时自动生成)',
    `dispose_method`         VARCHAR(20)   DEFAULT NULL COMMENT '处置方式:SELL出售 SCRAP报废 DONATE捐赠',
    `dispose_income`         DECIMAL(18,2) DEFAULT NULL COMMENT '清理收入(处置时收取)',
    `dispose_time`           DATETIME      DEFAULT NULL COMMENT '处置时间',
    `dispose_voucher_id`     BIGINT        DEFAULT NULL COMMENT '处置凭证ID(fin_voucher.id,处置时自动生成)',
    `remark`                 VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`              BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`            DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`              BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`            DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_asset_code` (`asset_code`),
    KEY `idx_category` (`category_id`),
    KEY `idx_dept` (`dept_id`),
    KEY `idx_status` (`status`),
    KEY `idx_account_date` (`account_date`),
    KEY `idx_supplier` (`supplier_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产卡片表';

-- ---------------------------------------------------------------------
-- 3. 折旧计提期间表（每期间 yyyyMM 一行，计提成功即 CLOSED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ast_depreciation` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `period`       VARCHAR(6)    NOT NULL COMMENT '会计期间(yyyyMM,唯一)',
    `status`       VARCHAR(10)   NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN开放 CLOSED已计提关闭(重复运行拒绝)',
    `voucher_id`   BIGINT        DEFAULT NULL COMMENT '本期间汇总折旧凭证ID(fin_voucher.id)',
    `asset_count`  INT           NOT NULL DEFAULT 0 COMMENT '本期间计提资产数',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '本期间计提总额',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_period` (`period`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '折旧计提期间表';

-- ---------------------------------------------------------------------
-- 4. 折旧计提明细表（期间 × 资产 一行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ast_depreciation_detail` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `period_id`    BIGINT        NOT NULL COMMENT '期间ID(ast_depreciation.id)',
    `asset_id`     BIGINT        NOT NULL COMMENT '资产ID(ast_asset.id)',
    `month_amount` DECIMAL(18,2) NOT NULL COMMENT '本月折旧额((原值-残值)/预计年限月,HALF_UP 2位)',
    `voucher_id`   BIGINT        NOT NULL COMMENT '折旧凭证ID(fin_voucher.id,同期间共用汇总凭证)',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_period_asset` (`period_id`, `asset_id`),
    KEY `idx_asset` (`asset_id`),
    KEY `idx_voucher` (`voucher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '折旧计提明细表';

-- ---------------------------------------------------------------------
-- 5. 种子：资产类别（科目映射按编码解析 fin_subject 真实 ID，不硬编码主键）
--    电子设备/运输设备/机器设备 → 固定资产1601 + 累计折旧1602，
--    折旧费用分别计入 管理费用6602 / 销售费用6601 / 制造费用5101
-- ---------------------------------------------------------------------
INSERT INTO `ast_category`
    (`code`, `name`, `depreciation_method`, `expected_life_months`, `residual_rate`,
     `asset_subject_id`, `accumulated_depreciation_subject_id`, `depreciation_expense_subject_id`,
     `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT 'DZ', '电子设备', 'AVG', 36, 5.00,
       s1.id, s2.id, s3.id,
       '电脑/打印机等办公电子设备,折旧计入管理费用',
       0, NOW(), 0, NOW(), 0
FROM `fin_subject` s1, `fin_subject` s2, `fin_subject` s3
WHERE s1.code = '1601' AND s1.deleted = 0
  AND s2.code = '1602' AND s2.deleted = 0
  AND s3.code = '6602' AND s3.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `ast_category` WHERE `code` = 'DZ');

INSERT INTO `ast_category`
    (`code`, `name`, `depreciation_method`, `expected_life_months`, `residual_rate`,
     `asset_subject_id`, `accumulated_depreciation_subject_id`, `depreciation_expense_subject_id`,
     `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT 'YS', '运输设备', 'AVG', 60, 5.00,
       s1.id, s2.id, s3.id,
       '车辆等运输设备,折旧计入销售费用',
       0, NOW(), 0, NOW(), 0
FROM `fin_subject` s1, `fin_subject` s2, `fin_subject` s3
WHERE s1.code = '1601' AND s1.deleted = 0
  AND s2.code = '1602' AND s2.deleted = 0
  AND s3.code = '6601' AND s3.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `ast_category` WHERE `code` = 'YS');

INSERT INTO `ast_category`
    (`code`, `name`, `depreciation_method`, `expected_life_months`, `residual_rate`,
     `asset_subject_id`, `accumulated_depreciation_subject_id`, `depreciation_expense_subject_id`,
     `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
SELECT 'JQ', '机器设备', 'AVG', 120, 3.00,
       s1.id, s2.id, s3.id,
       '生产机器设备,折旧计入制造费用',
       0, NOW(), 0, NOW(), 0
FROM `fin_subject` s1, `fin_subject` s2, `fin_subject` s3
WHERE s1.code = '1601' AND s1.deleted = 0
  AND s2.code = '1602' AND s2.deleted = 0
  AND s3.code = '5101' AND s3.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `ast_category` WHERE `code` = 'JQ');
