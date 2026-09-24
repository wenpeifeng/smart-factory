-- =====================================================================
-- V931 设备管理模块初始化（erp-equipment，二期）
-- 目的：
--   1) 设备管理 5 张业务表：设备台账 / 保养计划 / 保养执行记录 / 维修工单 / 工单备件明细；
--   2) 种子数据：3 台设备 + 1 条保养计划（next_date 取 CURDATE()+3 天，用于验证到期预警）。
-- 说明：
--   - 备件出库不建新表，按库存模块约定直接操作 inv_stock / inv_movement
--     （biz_type 使用本模块自建常量 EQP_OUT / EQP_IN，见 docs/modules/equipment.md）；
--   - 与 basedata 的边界：物料取自 base_material（仅引用 id，不建物理外键）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 设备台账
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eqp_device` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                VARCHAR(40)  NOT NULL COMMENT '设备编号(SB+yyyyMMdd+4位流水，可手工指定)',
    `name`                VARCHAR(100) NOT NULL COMMENT '设备名称',
    `model`               VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
    `manufacturer`        VARCHAR(100) DEFAULT NULL COMMENT '制造商',
    `purchase_date`       DATE         DEFAULT NULL COMMENT '购置日期',
    `original_value`      DECIMAL(18, 2) NOT NULL DEFAULT '0.00' COMMENT '购置原值(DECIMAL(18,2))',
    `workshop`            VARCHAR(100) DEFAULT NULL COMMENT '所在车间(文本)',
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '设备状态:1运行 2停机 3维修中 4报废(终态)',
    `responsible_person`  VARCHAR(50)  DEFAULT NULL COMMENT '责任人(文本)',
    `check_period_days`   INT          DEFAULT NULL COMMENT '点检周期(天),空=不点检',
    `remark`              VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`           BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_code` (`code`),
    KEY `idx_device_status` (`status`),
    KEY `idx_device_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备台账';

-- ---------------------------------------------------------------------
-- 2. 保养计划
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eqp_maintain_plan` (
    `id`            BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_id`     BIGINT  NOT NULL COMMENT '设备ID(eqp_device.id)',
    `maintain_type` TINYINT NOT NULL COMMENT '保养类型:1一级保养 2二级保养 3大修',
    `cycle_days`    INT     NOT NULL COMMENT '保养周期(天)',
    `last_date`     DATE    DEFAULT NULL COMMENT '上次保养日期',
    `next_date`     DATE    DEFAULT NULL COMMENT '下次保养日期(=上次+周期,服务端维护)',
    `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT  DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT  DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_plan_device` (`device_id`),
    KEY `idx_plan_next_date` (`next_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备保养计划';

-- ---------------------------------------------------------------------
-- 3. 保养执行记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eqp_maintain_record` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_id`     BIGINT      NOT NULL COMMENT '保养计划ID(eqp_maintain_plan.id)',
    `device_id`   BIGINT      NOT NULL COMMENT '设备ID(冗余,便于按设备查历史)',
    `execute_time` DATETIME   NOT NULL COMMENT '执行时间',
    `executor`    VARCHAR(50) DEFAULT NULL COMMENT '执行人(文本)',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '保养内容',
    `create_by`   BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_record_plan` (`plan_id`),
    KEY `idx_record_device` (`device_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备保养执行记录';

-- ---------------------------------------------------------------------
-- 4. 维修工单（头）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eqp_repair` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `repair_no`    VARCHAR(40)  NOT NULL COMMENT '工单号(WX+yyyyMMdd+4位流水)',
    `device_id`    BIGINT       NOT NULL COMMENT '设备ID(eqp_device.id)',
    `fault_desc`   VARCHAR(500) NOT NULL COMMENT '故障描述',
    `reporter`     VARCHAR(50)  DEFAULT NULL COMMENT '报修人(文本)',
    `report_time`  DATETIME     NOT NULL COMMENT '报修时间',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '工单状态:0待维修 1维修中 2已修复 3已关闭',
    `repairer`     VARCHAR(50)  DEFAULT NULL COMMENT '维修人(接单时填写)',
    `fault_reason` VARCHAR(500) DEFAULT NULL COMMENT '故障原因(完工时填写)',
    `warehouse_id` BIGINT       DEFAULT NULL COMMENT '备件出库仓库(base_warehouse.id,完工时领用备件)',
    `finish_time`  DATETIME     DEFAULT NULL COMMENT '完成时间',
    `remark`       VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`    BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_repair_no` (`repair_no`),
    KEY `idx_repair_device` (`device_id`),
    KEY `idx_repair_status` (`status`),
    KEY `idx_repair_report_time` (`report_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备维修工单';

-- ---------------------------------------------------------------------
-- 5. 维修工单备件明细（行）
--    cost_ref = 数量 × base_material.ref_cost（完工时点快照，仅供成本参考）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eqp_repair_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `repair_id`   BIGINT        NOT NULL COMMENT '工单ID(eqp_repair.id)',
    `material_id` BIGINT        NOT NULL COMMENT '物料ID(base_material.id)',
    `qty`         DECIMAL(18, 4) NOT NULL COMMENT '使用数量',
    `cost_ref`    DECIMAL(18, 2) NOT NULL DEFAULT '0.00' COMMENT '成本参考(数量×物料参考成本,完工时点快照)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_item_repair` (`repair_id`),
    KEY `idx_item_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '维修工单备件明细';

-- ---------------------------------------------------------------------
-- 6. 种子数据：3 台设备 + 1 条保养计划（管理员 create_by=1）
--    保养计划 next_date = CURDATE()+3，落在 7 天到期窗口内，便于验证 /plans/due
-- ---------------------------------------------------------------------
INSERT INTO `eqp_device`
    (`code`, `name`, `model`, `manufacturer`, `purchase_date`, `original_value`, `workshop`,
     `status`, `responsible_person`, `check_period_days`, `remark`, `create_by`, `update_by`)
VALUES ('SB202609010001', '数控车床 CK6140', 'CK6140x1000', '沈阳机床', '2023-03-15', 185000.00, '一金工车间', 1, '王建国', 7, NULL, 1, 1),
       ('SB202609010002', '立式加工中心 VMC850', 'VMC850', '大连机床', '2022-08-20', 468000.00, '二金工车间', 1, '李卫东', 15, NULL, 1, 1),
       ('SB202609010003', '空压机 SA-22A', 'SA-22A', ' Atlas 螺杆机', '2021-05-10', 42000.00, '动力站', 2, '赵四海', 30, '二级保养到期前重点巡检', 1, 1);

INSERT INTO `eqp_maintain_plan`
    (`device_id`, `maintain_type`, `cycle_days`, `last_date`, `next_date`, `remark`, `create_by`, `update_by`)
VALUES (1, 2, 30, DATE_SUB(CURDATE(), INTERVAL 27 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY),
        '导轨润滑/丝杠清洁/冷却液更换', 1, 1);
