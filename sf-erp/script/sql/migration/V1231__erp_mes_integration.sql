-- =====================================================================
-- V1231: ERP-MES 集成（erp-biz-integration，版本段 V12xx 集成段）
-- 契约：sf-mes/docs/MES与ERP协同设计.md（六类接口 + outbox/幂等/对账）。
-- 内容：
--   1) int_outbox          出站发件箱（ERP→MES 下推可靠投递，指数退避 1m/5m/30m/2h/6h）
--   2) int_message_log     入站消息日志（MES→ERP 回传幂等，uk: biz_type+biz_key+event_time）
--   3) mrp_work_order.mes_status  MES 执行状态镜像列（不改既有状态机语义）
--   4) sys_menu 种子        系统集成菜单与权限码（INSERT IGNORE 幂等，不写 org_id/tenant_id 列）
-- 幂等：建表 IF NOT EXISTS / 加列 information_schema 判断（与 V972 同款写法），可重放。
-- =====================================================================

-- ---------- 1. int_outbox 出站发件箱 ----------
CREATE TABLE IF NOT EXISTS `int_outbox` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `org_id`          BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=总厂)',
    `create_by`       BIGINT       NULL COMMENT '创建人用户ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT       NULL COMMENT '更新人用户ID',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    `biz_type`        VARCHAR(32)  NOT NULL COMMENT '业务类型:PRODUCTION_ORDER/MATERIAL/BOM/ROUTING',
    `biz_id`          BIGINT       NULL COMMENT '业务单据ID(来源表主键)',
    `event_time`      DATETIME(3)  NOT NULL COMMENT '事件时间(入队时刻)',
    `payload`         MEDIUMTEXT   NOT NULL COMMENT '报文JSON快照(重试时反序列化重发)',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待发/SUCCESS成功/DEAD死信',
    `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数(上限5)',
    `next_retry_time` DATETIME(3)  NULL COMMENT '下次重试时间(指数退避1m/5m/30m/2h/6h)',
    `error`           VARCHAR(500) NULL COMMENT '最近一次错误摘要',
    `success_time`    DATETIME(3)  NULL COMMENT '成功时间',
    PRIMARY KEY (`id`),
    KEY `idx_outbox_status_next` (`status`, `next_retry_time`),
    KEY `idx_outbox_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ERP-MES集成出站发件箱(ERP→MES下推)';

-- ---------- 2. int_message_log 入站消息日志 ----------
CREATE TABLE IF NOT EXISTS `int_message_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `org_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=总厂)',
    `create_by`   BIGINT       NULL COMMENT '创建人用户ID(MES回传无登录上下文,恒空)',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       NULL COMMENT '更新人用户ID',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    `biz_type`    VARCHAR(32)  NOT NULL COMMENT '业务类型:WORK_REPORT/PICKING/RETURN/QUALITY/ORDER_STATUS',
    `biz_key`     VARCHAR(128) NOT NULL COMMENT '业务幂等键(报工=woNo|工序,领料=pickNo,退料=returnNo,质检=inspectNo,状态=woNo|mesStatus)',
    `event_time`  DATETIME(3)  NOT NULL COMMENT '事件时间(幂等键成分)',
    `payload`     MEDIUMTEXT   NOT NULL COMMENT '报文JSON快照',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT '状态:SUCCESS已处理/DUPLICATE重复跳过',
    `error`       VARCHAR(500) NULL COMMENT '处理结果摘要/错误信息',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inbound_idem` (`biz_type`, `biz_key`, `event_time`),
    KEY `idx_inbound_biz_key` (`biz_type`, `biz_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ERP-MES集成入站消息日志(MES→ERP回传幂等)';

-- ---------- 3. mrp_work_order 加 mes_status 镜像列 ----------
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mrp_work_order' AND COLUMN_NAME = 'mes_status') = 0,
    'ALTER TABLE `mrp_work_order` ADD COLUMN `mes_status` VARCHAR(32) NULL COMMENT ''MES执行状态镜像(V1231:MES状态机透传,仅展示,不参与ERP状态机)'' AFTER `status`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. 菜单与权限码种子（不写 org_id/tenant_id 列，走列默认值；INSERT IGNORE 幂等） ----------
-- 360 系统集成(目录) / 361 集成监控(菜单,integration/monitor) / 权限码按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(360, 0,   '系统集成', 1, 'integration', NULL, NULL, 'ApiOutlined', 170, 1, 1),
(361, 360, '集成监控', 2, 'integration/monitor', 'integration/monitor/IntegrationMonitor', 'integration:outbox:list', NULL, 1, 1, 1),
(3611, 361, 'outbox手动重试', 3, NULL, NULL, 'integration:outbox:retry', NULL, 1, 1, 1),
(3612, 361, '入站日志查询', 3, NULL, NULL, 'integration:log:list', NULL, 2, 1, 1),
(3613, 361, '主数据对账推送', 3, NULL, NULL, 'integration:masterdata:push', NULL, 3, 1, 1);

-- super_admin(role_id=1) 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (360, 361, 3611, 3612, 3613);
