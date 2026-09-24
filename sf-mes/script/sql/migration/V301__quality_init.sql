-- =====================================================================
-- V301: 质量管理模块（quality，版本段 V30x）
-- 内容：
--   1) qc_template / qc_template_item  检验模板（主/子=检验项目）
--   2) qc_inspect / qc_inspect_item    检验单（主/子=模板快照+实检值）
--   3) qc_reject                       不合格品处置单
-- 检验类型: IQC来料(ERP采购入库/收货下推) IPQC过程(过站工序) FQC完工(工单末道工序) OQC出货
-- 来源单据类型(source_type): mat_receipt来料收货 exe_station过站记录 plan_workorder工单 NULL手工
-- 检验单状态机: 0草稿 -> 10已检验（条件UPDATE防并发，单向流转）
-- =====================================================================

-- 1. 检验模板主表
CREATE TABLE IF NOT EXISTS `qc_template` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_code` VARCHAR(40)   NOT NULL COMMENT '模板编码(唯一,建议 QT+序号)',
    `template_name` VARCHAR(100)  NOT NULL COMMENT '模板名称',
    `category_id`   BIGINT        DEFAULT NULL COMMENT '适用物料分类ID(base_material_category.id,NULL=全部分类通用)',
    `inspect_type`  VARCHAR(10)   NOT NULL COMMENT '检验类型:IQC来料 IPQC过程 FQC成品完工 OQC出货',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用(停用不参与自动带出)',
    `remark`        VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(质量模板共享0)',
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

-- 2. 检验模板明细（模板行=检验项目）
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
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(共享0)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`template_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检验模板明细表(检验项目)';

-- 3. 检验单主表
CREATE TABLE IF NOT EXISTS `qc_inspect` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inspect_no`       VARCHAR(30)   NOT NULL COMMENT '检验单号(QC+yyyyMMdd+4位流水)',
    `inspect_type`     VARCHAR(10)   NOT NULL COMMENT '检验类型:IQC来料 IPQC过程 FQC成品完工 OQC出货',
    `source_type`      VARCHAR(30)   DEFAULT NULL COMMENT '来源单据类型:mat_receipt来料收货 exe_station过站记录 plan_workorder工单 NULL手工',
    `source_id`        BIGINT        DEFAULT NULL COMMENT '来源单据ID(对应各模块主表id)',
    `source_no`        VARCHAR(40)   DEFAULT NULL COMMENT '来源单号(快照,便于列表展示)',
    `template_id`      BIGINT        DEFAULT NULL COMMENT '套用的检验模板ID(明细为其快照)',
    `workorder_id`     BIGINT        DEFAULT NULL COMMENT '关联工单ID(plan_workorder.id,IPQC/FQC)',
    `wo_step_id`       BIGINT        DEFAULT NULL COMMENT '关联工序任务ID(plan_wo_step.id,IPQC)',
    `material_id`      BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `supplier_id`      BIGINT        DEFAULT NULL COMMENT '供应商ID(base_supplier.id,IQC)',
    `batch_no`         VARCHAR(64)   DEFAULT NULL COMMENT '批次号/SN(IPQC/FQC/OQC)',
    `sample_qty`       DECIMAL(18,4) NOT NULL COMMENT '抽检数量(>0)',
    `qualified_qty`    DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '合格数量(提交检验时回填)',
    `unqualified_qty`  DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '不合格数量(提交检验时回填)',
    `conclusion`       TINYINT       DEFAULT NULL COMMENT '检验结论:1合格 2让步接收 3退货(草稿为NULL)',
    `inspector_name`   VARCHAR(50)   DEFAULT NULL COMMENT '检验人姓名(提交时录入)',
    `inspect_date`     DATE          DEFAULT NULL COMMENT '检验日期(提交时录入)',
    `status`           TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0草稿 10已检验',
    `submit_time`      DATETIME      DEFAULT NULL COMMENT '提交检验时间',
    `remark`           VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `tenant_id`        BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`           BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`        BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inspect_no` (`inspect_no`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_workorder` (`workorder_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_inspect_type` (`inspect_type`),
    KEY `idx_inspect_date` (`inspect_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检验单主表';

-- 4. 检验单明细（检验项目=模板快照，补录实检值与单项判定）
CREATE TABLE IF NOT EXISTS `qc_inspect_item` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inspect_id`      BIGINT        NOT NULL COMMENT '检验单ID(qc_inspect.id)',
    `item_name`       VARCHAR(100)  NOT NULL COMMENT '检验项目名称(模板快照)',
    `standard_value`  VARCHAR(100)  DEFAULT NULL COMMENT '标准值(模板快照)',
    `lower_limit`     DECIMAL(18,4) DEFAULT NULL COMMENT '标准下限(模板快照)',
    `upper_limit`     DECIMAL(18,4) DEFAULT NULL COMMENT '标准上限(模板快照)',
    `inspect_method`  VARCHAR(100)  DEFAULT NULL COMMENT '检验方法(模板快照)',
    `sample_rate`     DECIMAL(5,2)  NOT NULL DEFAULT 100.00 COMMENT '抽检比例%(模板快照)',
    `actual_value`    VARCHAR(200)  DEFAULT NULL COMMENT '实检值(文本/数值均可)',
    `line_result`     TINYINT       DEFAULT NULL COMMENT '单项判定:1合格 0不合格 NULL未判定',
    `seq`             INT           NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_inspect_id` (`inspect_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检验单明细表(检验项目+实检值)';

-- 5. 不合格品处置单（从已检验且不合格数>0的检验单生成；决定=返工/报废时联动 execution 生成返工报废单）
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
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`          BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reject_no` (`reject_no`),
    KEY `idx_inspect_id` (`inspect_id`),
    KEY `idx_material_id` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '不合格品处置单';
