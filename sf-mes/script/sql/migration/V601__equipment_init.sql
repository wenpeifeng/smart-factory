-- =====================================================================
-- V601: 设备管理模块（equipment，版本段 V60x）
-- 内容：
--   1) eqp_device    设备台账（工作中心资源，过站报工 device_id 引用）
--   2) eqp_check     设备点检记录
--   3) eqp_maintain  设备保养/维修记录
-- OEE 不落表：由过站记录 + 排班日历实时聚合计算（equipment 模块接口）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `eqp_device` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`           VARCHAR(40)  NOT NULL COMMENT '设备编码(SB+yyyyMMdd+4位流水)',
    `name`           VARCHAR(100) NOT NULL COMMENT '设备名称',
    `model`          VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
    `workcenter_id`  BIGINT       DEFAULT NULL COMMENT '所属工作中心ID(base_workcenter.id)',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'IDLE' COMMENT '状态:RUNNING运行 IDLE闲置 DOWN停机 REPAIR维修',
    `location`       VARCHAR(100) DEFAULT NULL COMMENT '安装位置',
    `buy_date`       DATE         DEFAULT NULL COMMENT '购置日期',
    `remark`         VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`         BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID',
    `create_by`      BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_code` (`code`),
    KEY `idx_device_workcenter` (`workcenter_id`),
    KEY `idx_device_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备台账';

CREATE TABLE IF NOT EXISTS `eqp_check` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `check_no`     VARCHAR(40)  NOT NULL COMMENT '点检单号(DJ+yyyyMMdd+4位流水)',
    `device_id`    BIGINT       NOT NULL COMMENT '设备ID(eqp_device.id)',
    `checker_name` VARCHAR(50)  NOT NULL COMMENT '点检人',
    `result`       VARCHAR(10)  NOT NULL COMMENT '点检结果:OK正常 NG异常',
    `check_time`   DATETIME     NOT NULL COMMENT '点检时间',
    `remark`       VARCHAR(500) DEFAULT NULL COMMENT '异常说明/备注',
    `tenant_id`    BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID',
    `create_by`    BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_check_no` (`check_no`),
    KEY `idx_check_device` (`device_id`, `check_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备点检记录';

CREATE TABLE IF NOT EXISTS `eqp_maintain` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `maintain_no`      VARCHAR(40)  NOT NULL COMMENT '保养/维修单号(BY+yyyyMMdd+4位流水)',
    `device_id`        BIGINT       NOT NULL COMMENT '设备ID(eqp_device.id)',
    `type`             VARCHAR(10)  NOT NULL COMMENT '类型:PM保养 REPAIR维修',
    `plan_date`        DATE         DEFAULT NULL COMMENT '计划日期',
    `maintainer`       VARCHAR(50)  DEFAULT NULL COMMENT '执行人',
    `duration_minutes` INT          DEFAULT NULL COMMENT '耗时(分钟)',
    `status`           VARCHAR(10)  NOT NULL DEFAULT 'PLAN' COMMENT '状态:PLAN计划 DONE完成',
    `content`          VARCHAR(500) DEFAULT NULL COMMENT '保养/维修内容',
    `finish_time`      DATETIME     DEFAULT NULL COMMENT '完成时间',
    `remark`           VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`        BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`           BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID',
    `create_by`        BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`      DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_maintain_no` (`maintain_no`),
    KEY `idx_maintain_device` (`device_id`, `plan_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备保养/维修记录';
