-- =====================================================================
-- V311: SPC 统计过程控制（quality 模块，版本段 V30x）
-- 内容：
--   1) qc_spc_rule   SPC 控制规则（物料 x 检验项目 → 控制限配置，含判异准则开关）
--   2) qc_spc_sample SPC 样本（从已检验明细中的数值型实检值自动采集）
-- 判异准则（GB/T 4091 / 西电电气 8 条，本系统实现前 5 条高频项，规则可开关）：
--   R1 1点落在A区以外(超出控制限)  R2 连续9点在中心线同一侧
--   R3 连续6点递增或递减  R4 连续14点上下交替  R5 3点中2点落在同侧B区以外
-- =====================================================================

CREATE TABLE IF NOT EXISTS `qc_spc_rule` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id`   BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `item_name`     VARCHAR(100)  NOT NULL COMMENT '检验项目名称(与检验模板项目名对齐)',
    `ucl`           DECIMAL(18,4) NOT NULL COMMENT '控制上限UCL',
    `lcl`           DECIMAL(18,4) NOT NULL COMMENT '控制下限LCL',
    `target`        DECIMAL(18,4) DEFAULT NULL COMMENT '目标值/中心线(空=(UCL+LCL)/2)',
    `rule_r1`       TINYINT       NOT NULL DEFAULT 1 COMMENT '准则R1(超限):1启用 0停用',
    `rule_r2`       TINYINT       NOT NULL DEFAULT 1 COMMENT '准则R2(连续9点同侧):1启用',
    `rule_r3`       TINYINT       NOT NULL DEFAULT 1 COMMENT '准则R3(连续6点趋势):1启用',
    `rule_r4`       TINYINT       NOT NULL DEFAULT 0 COMMENT '准则R4(连续14点交替):0默认停用',
    `rule_r5`       TINYINT       NOT NULL DEFAULT 1 COMMENT '准则R5(3点中2点同侧B区外):1启用',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`        VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spc_rule` (`material_id`, `item_name`),
    KEY `idx_spc_rule_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'SPC控制规则';

CREATE TABLE IF NOT EXISTS `qc_spc_sample` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_id`       BIGINT        NOT NULL COMMENT '规则ID(qc_spc_rule.id)',
    `inspect_id`    BIGINT        NOT NULL COMMENT '来源检验单ID(qc_inspect.id)',
    `value`         DECIMAL(18,4) NOT NULL COMMENT '样本值(数值型实检值)',
    `sample_time`   DATETIME      NOT NULL COMMENT '采样时间(检验提交时间)',
    `violations`    VARCHAR(100)  DEFAULT NULL COMMENT '命中的判异准则(逗号分隔,如R1,R3;空=正常)',
    `tenant_id`     BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`        BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`     BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spc_sample_inspect` (`rule_id`, `inspect_id`),
    KEY `idx_spc_sample_time` (`sample_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'SPC样本(检验数值自动采集)';
