-- =====================================================================
-- V971: 预算管理模块建表（erp-biz-budget，版本段 V97x）
-- 内容：
--   1) budget_plan            预算方案（状态机 DRAFT草稿 -> RELEASED已下达 -> CLOSED已关闭）
--   2) budget_item            预算明细（方案×部门×科目×月份 金额，四元组唯一）
--   3) budget_control_config  预算控制配置（每方案一条：WARN警告放行 / BLOCK阻止 + 阈值%）
--   4) budget_usage_record    预算执行数登记（付款/费用发生推式记账，跨模块接线用）
-- 跨模块约定（只读约定表，不 import 他人业务代码，见 docs/modules/budget.md）：
--   * sys_dept / sys_user（system 模块 V11/V12）—— 预算编制维度：部门、编制人；
--   * fin_subject / fin_voucher / fin_voucher_entry（finance 模块 V501）——
--     科目（限末级费用/成本类）、凭证口径已用数 =
--     fin_voucher(POSTED) × fin_voucher_entry(该科目借方) × 制单人部门(sys_user.dept_id)；
--   * fin_payment（finance 模块 V501）无部门/科目列，付款类执行数无法按约定表直接归属，
--     由 finance 模块在付款单创建/审核时调用 BudgetControlService.record() 推入
--     budget_usage_record（本期接线约定，主线程后续接线；演示用 POST /budget/usage）。
-- =====================================================================

-- ---------- 1. 预算方案主表 ----------
CREATE TABLE IF NOT EXISTS `budget_plan`
(
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_code`   VARCHAR(40)   NOT NULL COMMENT '方案编码(全局唯一,建议 YS+年度+序号,如 YS2026-001)',
    `plan_name`   VARCHAR(100)  NOT NULL COMMENT '方案名称',
    `budget_year` INT           NOT NULL COMMENT '预算年度(2000-2100)',
    `status`      VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿(可改) RELEASED已下达(锁定禁改) CLOSED已关闭',
    `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '预算总额(明细保存时冗余重算)',
    `prepare_by`  BIGINT        NOT NULL DEFAULT 0 COMMENT '编制人(sys_user.id)',
    `remark`      VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_code` (`plan_code`),
    KEY `idx_budget_year` (`budget_year`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预算方案主表';

-- ---------- 2. 预算明细（部门×科目×月份 矩阵单元格） ----------
-- uk 四元组含逻辑删行：保存走 upsert(ON DUPLICATE KEY 顺带复活 deleted=0)，不做删后重插。
CREATE TABLE IF NOT EXISTS `budget_item`
(
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_id`    BIGINT        NOT NULL COMMENT '方案ID(budget_plan.id)',
    `dept_id`    BIGINT        NOT NULL COMMENT '部门ID(sys_dept.id)',
    `subject_id` BIGINT        NOT NULL COMMENT '科目ID(fin_subject.id,限末级费用/成本类)',
    `month`      INT           NOT NULL COMMENT '月份(1-12)',
    `amount`     DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '预算金额(>=0)',
    `create_by`  BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`  BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_dept_subject_month` (`plan_id`, `dept_id`, `subject_id`, `month`),
    KEY `idx_dept_subject` (`dept_id`, `subject_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预算明细表(方案x部门x科目x月份)';

-- ---------- 3. 预算控制配置（每方案一条） ----------
CREATE TABLE IF NOT EXISTS `budget_control_config`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_id`      BIGINT       NOT NULL COMMENT '方案ID(budget_plan.id)',
    `control_way`  VARCHAR(10)  NOT NULL DEFAULT 'WARN' COMMENT '控制方式:WARN超预算警告放行 BLOCK超预算阻止',
    `over_percent` DECIMAL(5,2) NOT NULL DEFAULT 100.00 COMMENT '超预算阈值%(占用率>=该值触发控制,默认100)',
    `create_by`    BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_plan_id` (`plan_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预算控制配置表';

-- ---------- 4. 预算执行数登记（付款等场景推式记账） ----------
-- finance 模块接线：付款单(fin_payment)创建/审核时按 部门×科目×期间 调
-- BudgetControlService.record() 落一条 PAYMENT 记录； MANUAL 供手工登记/冲销调整。
CREATE TABLE IF NOT EXISTS `budget_usage_record`
(
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dept_id`     BIGINT        NOT NULL COMMENT '部门ID(sys_dept.id)',
    `subject_id`  BIGINT        NOT NULL COMMENT '科目ID(fin_subject.id,费用/成本类)',
    `year`        INT           NOT NULL COMMENT '年度',
    `month`       INT           NOT NULL COMMENT '月份(1-12)',
    `amount`      DECIMAL(18,2) NOT NULL COMMENT '发生金额(冲销传负数)',
    `source_type` VARCHAR(20)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源:PAYMENT付款 VOUCHER凭证 MANUAL手工',
    `source_id`   BIGINT        DEFAULT NULL COMMENT '来源单据ID(如 fin_payment.id)',
    `source_no`   VARCHAR(40)   DEFAULT NULL COMMENT '来源单据号(如 FK202609050001)',
    `biz_date`    DATE          DEFAULT NULL COMMENT '业务日期',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_dept_subject_period` (`dept_id`, `subject_id`, `year`, `month`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_source_no` (`source_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预算执行数登记表(推式记账)';
