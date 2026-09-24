-- =====================================================================
-- V701: 质量管理模块建表（erp-qc，版本段 V70x）
-- 内容：
--   1) qc_template / qc_template_item  检验模板（主/子=检验项目）
--   2) qc_inspect / qc_inspect_item    检验单（主/子=按模板快照的检验项目+实检值）
--   3) qc_reject                       不合格品处置单
-- 约定：
--   * 所有表带 id/create_by/create_time/update_by/update_time/deleted 六列；
--   * 数量 DECIMAL(18,4)，抽检比例 DECIMAL(5,2)（百分数 0-100）；
--   * 状态列注释标明取值，检验单状态机 0草稿 -> 10已检验 单向流转；
--   * 本模块【不创建】他人模块的表：pur_instock/pur_instock_detail（V201）、
--     mrp_work_order（V601）、sal_return/sal_return_detail（V303）、
--     base_material/base_supplier/base_material_category/base_unit（V101）
--     由各自 owner 脚本建表，qc 模块只按跨模块约定【只读】查询
--     （见 docs/modules/qc.md，不 import 他人业务代码）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 检验模板主表
-- 检验类型: IQC来料检验 IPQC过程检验 FQC成品完工检验 OQC出货检验
-- category_id 为空表示不限分类（通用模板），匹配时精确分类优先于通用。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qc_template` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_code` VARCHAR(40)   NOT NULL COMMENT '模板编码(唯一,建议 QT+序号)',
    `template_name` VARCHAR(100)  NOT NULL COMMENT '模板名称',
    `category_id`   BIGINT        DEFAULT NULL COMMENT '适用物料分类ID(base_material_category.id,NULL=全部分类通用)',
    `inspect_type`  VARCHAR(10)   NOT NULL COMMENT '检验类型:IQC来料 IPQC过程 FQC成品完工 OQC出货',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用(停用不参与自动带出)',
    `remark`        VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_inspect_type` (`inspect_type`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检验模板主表';

-- ---------------------------------------------------------------------
-- 2. 检验模板明细表（模板行=检验项目）
-- 上下限均空表示不限定数值范围（定性项目如外观目测）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qc_template_item` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_id`     BIGINT        NOT NULL COMMENT '模板ID(qc_template.id)',
    `item_name`       VARCHAR(100)  NOT NULL COMMENT '检验项目名称(如外观/尺寸/耐压)',
    `standard_value`  VARCHAR(100)  DEFAULT NULL COMMENT '标准值(文本或数值,如 10.00±0.10)',
    `lower_limit`     DECIMAL(18,4) DEFAULT NULL COMMENT '标准下限(空=不限)',
    `upper_limit`     DECIMAL(18,4) DEFAULT NULL COMMENT '标准上限(空=不限)',
    `inspect_method`  VARCHAR(100)  DEFAULT NULL COMMENT '检验方法(如目测/千分尺/耐压测试)',
    `sample_rate`     DECIMAL(5,2)  NOT NULL DEFAULT 100.00 COMMENT '抽检比例%(0-100)',
    `seq`             INT           NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`template_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检验模板明细表(检验项目)';

-- ---------------------------------------------------------------------
-- 3. 检验单主表
-- 状态机: 0草稿 -> 10已检验（条件UPDATE防并发重复提交，只允许单向流转）
-- 来源单据类型(source_type): pur_instock采购入库(IQC) / mrp_work_order生产工单(FQC)
--                           / sal_return销售退货(IQC复检) / NULL手工创建
-- 检验结论(conclusion): 1合格 2让步接收 3退货（提交检验时必填）
-- 口径约束: 合格数+不合格数=抽检数；结论=1时不合格数必须为0，结论=2/3时不合格数必须>0
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qc_inspect` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inspect_no`       VARCHAR(30)   NOT NULL COMMENT '检验单号(QC+yyyyMMdd+4位流水)',
    `inspect_type`     VARCHAR(10)   NOT NULL COMMENT '检验类型:IQC来料 IPQC过程 FQC成品完工 OQC出货',
    `source_type`      VARCHAR(30)   DEFAULT NULL COMMENT '来源单据类型:pur_instock采购入库 mrp_work_order生产工单 sal_return销售退货 NULL手工',
    `source_id`        BIGINT        DEFAULT NULL COMMENT '来源单据ID(对应各模块主表id)',
    `source_no`        VARCHAR(40)   DEFAULT NULL COMMENT '来源单号(快照,便于列表展示)',
    `template_id`      BIGINT        DEFAULT NULL COMMENT '套用的检验模板ID(明细为其快照,模板后续修改不影响本单)',
    `material_id`      BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `supplier_id`      BIGINT        DEFAULT NULL COMMENT '供应商ID(base_supplier.id,IQC来源采购入库时填写)',
    `sample_qty`       DECIMAL(18,4) NOT NULL COMMENT '抽检数量(>0,默认按模板抽检比例x来源数量向上取整,至少1)',
    `qualified_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '合格数量(提交检验时回填)',
    `unqualified_qty`  DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '不合格数量(提交检验时回填)',
    `conclusion`       TINYINT       DEFAULT NULL COMMENT '检验结论:1合格 2让步接收 3退货(草稿为NULL)',
    `inspector_name`   VARCHAR(50)   DEFAULT NULL COMMENT '检验人姓名(提交检验时录入)',
    `inspect_date`     DATE          DEFAULT NULL COMMENT '检验日期(提交检验时录入)',
    `status`           TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已检验',
    `submit_time`      DATETIME      DEFAULT NULL COMMENT '提交检验时间',
    `remark`           VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`        BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inspect_no` (`inspect_no`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_inspect_type` (`inspect_type`),
    KEY `idx_status` (`status`),
    KEY `idx_inspect_date` (`inspect_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检验单主表';

-- ---------------------------------------------------------------------
-- 4. 检验单明细表（检验项目来自模板快照，可补录实检值与单项判定）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qc_inspect_item` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inspect_id`      BIGINT        NOT NULL COMMENT '检验单ID(qc_inspect.id)',
    `item_name`       VARCHAR(100)  NOT NULL COMMENT '检验项目名称(模板快照)',
    `standard_value`  VARCHAR(100)  DEFAULT NULL COMMENT '标准值(模板快照)',
    `lower_limit`     DECIMAL(18,4) DEFAULT NULL COMMENT '标准下限(模板快照)',
    `upper_limit`     DECIMAL(18,4) DEFAULT NULL COMMENT '标准上限(模板快照)',
    `inspect_method`  VARCHAR(100)  DEFAULT NULL COMMENT '检验方法(模板快照)',
    `sample_rate`     DECIMAL(5,2)  NOT NULL DEFAULT 100.00 COMMENT '抽检比例%(模板快照)',
    `actual_value`    VARCHAR(200)  DEFAULT NULL COMMENT '实检值(文本/数值均可,提交检验时录入)',
    `line_result`     TINYINT       DEFAULT NULL COMMENT '单项判定:1合格 0不合格 NULL未判定',
    `seq`             INT           NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_inspect_id` (`inspect_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检验单明细表(检验项目+实检值)';

-- ---------------------------------------------------------------------
-- 5. 不合格品处置单（从已检验且不合格数>0的检验单生成）
-- 处置决定(decision): 1退货 2返工 3让步接收 4报废
-- 约束: 同一检验单累计处置数量 <= 不合格数量（服务层校验）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qc_reject` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `reject_no`       VARCHAR(30)   NOT NULL COMMENT '处置单号(RJ+yyyyMMdd+4位流水)',
    `inspect_id`      BIGINT        NOT NULL COMMENT '来源检验单ID(qc_inspect.id)',
    `inspect_no`      VARCHAR(30)   NOT NULL COMMENT '来源检验单号(快照)',
    `material_id`     BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`             DECIMAL(18,4) NOT NULL COMMENT '处置数量(>0,累计<=检验单不合格数)',
    `decision`        TINYINT       NOT NULL COMMENT '处置决定:1退货 2返工 3让步接收 4报废',
    `dispose_remark`  VARCHAR(500)  DEFAULT NULL COMMENT '处置说明',
    `approver`        VARCHAR(50)   DEFAULT NULL COMMENT '审批人',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reject_no` (`reject_no`),
    KEY `idx_inspect_id` (`inspect_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_decision` (`decision`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '不合格品处置单';
