-- =====================================================================
-- V102: MES 制造主数据（basedata 模块，版本段 V10x）
-- 内容：
--   1) base_bom / base_bom_item           BOM 物料清单（头/行）
--   2) base_operation                      标准工序库
--   3) base_routing / base_routing_step    工艺路线（头/工序步骤）
--   4) base_workcenter                     工作中心（排程/OEE 的资源单元）
--   5) base_team / base_team_member        班组（头/成员）
--   6) base_shift_calendar                 班组排班日历
-- 约定：新表规范 —— 每表 8 公共列（tenant_id/org_id 默认 1000/0，主数据共享语义）；
--       数量 DECIMAL(18,4)，BOM 用量 DECIMAL(18,6)，工时(分钟) DECIMAL(8,2)。
-- 来源：物料/仓库/单位/客商沿用 V101；本脚本补齐 MES 侧制造主数据。
-- =====================================================================

-- 1. BOM 头（成品 + 版本）
CREATE TABLE IF NOT EXISTS `base_bom` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id` BIGINT       NOT NULL COMMENT '成品物料ID(base_material.id)',
    `version`     VARCHAR(20)  NOT NULL COMMENT 'BOM版本号(如 V1.0)',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 ACTIVE已审核(有效) DISABLED停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(共享主数据0)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bom_material_version` (`material_id`, `version`),
    KEY `idx_bom_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM物料清单头';

-- 2. BOM 子件明细
CREATE TABLE IF NOT EXISTS `base_bom_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `bom_id`      BIGINT        NOT NULL COMMENT 'BOM头ID(base_bom.id)',
    `material_id` BIGINT        NOT NULL COMMENT '子件物料ID(base_material.id)',
    `qty`         DECIMAL(18,6) NOT NULL COMMENT '单位用量(单件成品耗用子件数量)',
    `loss_rate`   DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '损耗率%(0-100)',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(共享主数据0)',
    `create_by`   BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_bom_item_bom` (`bom_id`),
    KEY `idx_bom_item_material` (`material_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM子件明细';

-- 3. 标准工序库（供工艺路线步骤引用）
CREATE TABLE IF NOT EXISTS `base_operation` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                   VARCHAR(40)  NOT NULL COMMENT '编码(OP+yyyyMMdd+4位流水)',
    `name`                   VARCHAR(100) NOT NULL COMMENT '工序名称',
    `default_workcenter_id`  BIGINT       DEFAULT NULL COMMENT '默认工作中心ID(base_workcenter.id)',
    `setup_minutes`          INT          NOT NULL DEFAULT 0 COMMENT '准备时间(分钟/批)',
    `unit_minutes`           DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '单位加工时间(分钟/件)',
    `outsource_flag`         TINYINT      NOT NULL DEFAULT 0 COMMENT '外协标志:1外协 0自制',
    `qc_flag`                TINYINT      NOT NULL DEFAULT 0 COMMENT '是否质检点:1是(IPQC挂点) 0否',
    `status`                 VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED启用 DISABLED停用',
    `remark`                 VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`              BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`                 BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(共享主数据0)',
    `create_by`              BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`              BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_operation_code` (`code`),
    KEY `idx_operation_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '标准工序库';

-- 4. 工艺路线头（成品物料 + 版本；工单下达时取 ACTIVE 版本生成工序任务）
CREATE TABLE IF NOT EXISTS `base_routing` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `material_id` BIGINT       NOT NULL COMMENT '成品物料ID(base_material.id)',
    `version`     VARCHAR(20)  NOT NULL COMMENT '工艺路线版本号(如 V1.0)',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿 ACTIVE已审核 DISABLED停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(共享主数据0)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_routing_material_version` (`material_id`, `version`),
    KEY `idx_routing_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工艺路线头';

-- 5. 工艺路线工序步骤
CREATE TABLE IF NOT EXISTS `base_routing_step` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `routing_id`     BIGINT       NOT NULL COMMENT '工艺路线ID(base_routing.id)',
    `seq_no`         INT          NOT NULL COMMENT '序号(工序顺序,从1起)',
    `operation_id`   BIGINT       NOT NULL COMMENT '工序ID(base_operation.id)',
    `workcenter_id`  BIGINT       DEFAULT NULL COMMENT '工作中心ID(空=取工序默认工作中心)',
    `unit_minutes`   DECIMAL(8,2) DEFAULT NULL COMMENT '单件工时分钟(空=取工序单位加工时间)',
    `report_flag`    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否报工点:1是 0否',
    `key_component_flag` TINYINT  NOT NULL DEFAULT 0 COMMENT '是否关键件采集点:1过站需扫码关键件SN',
    `remark`         VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`         BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(共享主数据0)',
    `create_by`      BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_rs_routing` (`routing_id`),
    KEY `idx_rs_operation` (`operation_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工艺路线工序步骤';

-- 6. 工作中心（排程/OEE 的资源单元）
CREATE TABLE IF NOT EXISTS `base_workcenter` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                  VARCHAR(40)  NOT NULL COMMENT '编码(WC+yyyyMMdd+4位流水)',
    `name`                  VARCHAR(100) NOT NULL COMMENT '工作中心名称',
    `workshop`              VARCHAR(100) DEFAULT NULL COMMENT '所属车间(文本)',
    `capacity_hours_per_day` DECIMAL(8,2) NOT NULL DEFAULT 8 COMMENT '每日产能工时(小时/天)',
    `equipment_count`       INT          NOT NULL DEFAULT 1 COMMENT '设备数',
    `team_id`               BIGINT       DEFAULT NULL COMMENT '默认班组ID(base_team.id)',
    `status`                VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED启用 DISABLED停用',
    `remark`                VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`             BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`                BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(资源归属业务组织,默认1总厂)',
    `create_by`             BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workcenter_code` (`code`),
    KEY `idx_workcenter_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作中心';

-- 7. 班组
CREATE TABLE IF NOT EXISTS `base_team` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(40)  NOT NULL COMMENT '班组编码(BZ+yyyyMMdd+4位流水)',
    `name`        VARCHAR(100) NOT NULL COMMENT '班组名称(如装配一班)',
    `leader`      VARCHAR(50)  DEFAULT NULL COMMENT '班组长(文本)',
    `workcenter_id` BIGINT     DEFAULT NULL COMMENT '所属工作中心ID(base_workcenter.id)',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED启用 DISABLED停用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组';

-- 8. 班组成员
CREATE TABLE IF NOT EXISTS `base_team_member` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `team_id`     BIGINT       NOT NULL COMMENT '班组ID(base_team.id)',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '用户ID(sys_user.id,本地账号)',
    `member_name` VARCHAR(50)  NOT NULL COMMENT '成员姓名',
    `skill_level` VARCHAR(20)  DEFAULT NULL COMMENT '技能等级:J1初级 J2中级 J3高级 J4专家',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_team_member_team` (`team_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组成员';

-- 9. 班组排班日历（一天一班一行）
CREATE TABLE IF NOT EXISTS `base_shift_calendar` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `team_id`     BIGINT       NOT NULL COMMENT '班组ID(base_team.id)',
    `work_date`   DATE         NOT NULL COMMENT '排班日期',
    `shift`       VARCHAR(10)  NOT NULL COMMENT '班次:DAY白班 MIDDLE中班 NIGHT夜班',
    `hours`       DECIMAL(8,2) NOT NULL DEFAULT 8 COMMENT '班次工时(小时,OEE负荷时间基数)',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000)',
    `org_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(默认1总厂)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shift_team_date` (`team_id`, `work_date`, `shift`),
    KEY `idx_shift_date` (`work_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组排班日历';
