-- =====================================================================
-- V921: 人力资源模块(erp-hr)初始表（hr 使用 V92x 段）
-- 范围：员工档案 / 考勤 / 计件价目 / 计件工资期间+明细
-- 约定：所有表统一带 id/create_by/create_time/update_by/update_time/deleted；
--       金额 DECIMAL(18,2)，数量 DECIMAL(18,4)。
-- 跨模块约定列（只读，不建表）：
--   sys_dept(id, name)                     —— 员工所属部门
--   base_material(id, code, name, status)  —— 计件价目物料
--   mrp_work_order(id, order_no, material_id, finish_qty, status,
--                  create_by, update_time) —— 计件归集完工数据来源
-- ★ erp-cost 并行协作契约：hr_piece_wage_detail 必含
--   (work_order_id, employee_id, amount) 三列，cost 按此归集工单人工成本。
-- =====================================================================

-- 1. 员工档案
CREATE TABLE IF NOT EXISTS `hr_employee` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `emp_no`      VARCHAR(30)   NOT NULL COMMENT '工号(EMP+yyyyMMdd+4位流水，系统生成)',
    `name`        VARCHAR(30)   NOT NULL COMMENT '姓名',
    `gender`      TINYINT       NOT NULL DEFAULT 1 COMMENT '性别:1男 2女',
    `phone`       VARCHAR(20)   DEFAULT NULL COMMENT '手机号',
    `id_card`     VARCHAR(18)   DEFAULT NULL COMMENT '身份证号(返回时脱敏)',
    `dept_id`     BIGINT        DEFAULT NULL COMMENT '部门ID(sys_dept.id)',
    `position`    VARCHAR(50)   DEFAULT NULL COMMENT '岗位',
    `hire_date`   DATE          NOT NULL COMMENT '入职日期',
    `resign_date` DATE          DEFAULT NULL COMMENT '离职日期(离职办理时填写)',
    `status`      VARCHAR(20)   NOT NULL DEFAULT 'ON_JOB' COMMENT '状态:ON_JOB在职 RESIGNED离职',
    `base_salary` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '月薪基准(缺勤扣款基数)',
    `user_id`     BIGINT        DEFAULT NULL COMMENT '关联登录账号(sys_user.id，计件归集按工单制单人归属员工)',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_no` (`emp_no`),
    KEY `idx_dept` (`dept_id`),
    KEY `idx_status` (`status`),
    KEY `idx_user` (`user_id`),
    KEY `idx_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工档案';

-- 2. 考勤记录（一员工一天一行，CSV 导入重复覆盖）
CREATE TABLE IF NOT EXISTS `hr_attendance` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `employee_id` BIGINT       NOT NULL COMMENT '员工ID(hr_employee.id)',
    `att_date`    DATE         NOT NULL COMMENT '考勤日期',
    `status`      VARCHAR(20)  NOT NULL COMMENT '状态:NORMAL正常 ABSENT缺勤 LATE迟到 LEAVE请假 OVERTIME加班',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_date` (`employee_id`, `att_date`),
    KEY `idx_date` (`att_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '考勤记录';

-- 3. 计件价目表（物料 × 工序 单价）
CREATE TABLE IF NOT EXISTS `hr_piece_price` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id`  BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `process_name` VARCHAR(50)   NOT NULL COMMENT '工序名称(如 冲压/焊接/装配)',
    `unit_price`   DECIMAL(18,2) NOT NULL COMMENT '计件单价(元/件)',
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED启用 DISABLED停用',
    `remark`       VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_material_process` (`material_id`, `process_name`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计件价目表';

-- 4. 计件工资期间（一月一期间）
CREATE TABLE IF NOT EXISTS `hr_piece_wage` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `period`      VARCHAR(7)  NOT NULL COMMENT '期间(yyyy-MM)',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN开账(可归集) CLOSED已结账',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_period` (`period`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计件工资期间';

-- 5. 计件工资明细（归集自完工生产订单；cost 模块按 work_order_id+employee_id+amount 读人工成本）
CREATE TABLE IF NOT EXISTS `hr_piece_wage_detail` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `period_id`    BIGINT        NOT NULL COMMENT '期间ID(hr_piece_wage.id)',
    `employee_id`  BIGINT        NOT NULL COMMENT '员工ID(hr_employee.id)',
    `work_order_id` BIGINT       NOT NULL COMMENT '生产订单ID(mrp_work_order.id，来源可追溯)',
    `work_order_no` VARCHAR(30)  NOT NULL COMMENT '生产订单号快照(归集时可追溯)',
    `material_id`  BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `process_name` VARCHAR(50)   NOT NULL COMMENT '工序名称(取价目表)',
    `qty`          DECIMAL(18,4) NOT NULL COMMENT '完成数量(取订单累计完工良品数)',
    `unit_price`   DECIMAL(18,2) NOT NULL COMMENT '单价(取价目表)',
    `amount`       DECIMAL(18,2) NOT NULL COMMENT '金额=数量×单价(2位 HALF_UP)',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_period_workorder` (`period_id`, `work_order_id`),
    KEY `idx_employee` (`employee_id`),
    KEY `idx_work_order` (`work_order_id`),
    KEY `idx_period` (`period_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计件工资明细';

-- 6. 种子：2 条计件价目（对准已有 base_material 真实ID）
--    id=3 WL-STEEL-001 冷轧钢板 / id=4 WL-BOLT-002 六角螺栓
INSERT INTO `hr_piece_price`
    (`material_id`, `process_name`, `unit_price`, `status`, `remark`, `create_by`, `update_by`)
VALUES
    (3, '冲压成型', 12.50, 'ENABLED', '种子数据：冷轧钢板冲压工序计件单价', 0, 0),
    (4, '冷镦成型', 0.80, 'ENABLED', '种子数据：六角螺栓冷镦工序计件单价', 0, 0);
