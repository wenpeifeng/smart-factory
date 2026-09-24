-- =====================================================================
-- V1051: 统一通知中心（erp-platform-notify）初始表
-- 范围：通知通道配置(notify_channel_config) / 发送日志(notify_send_log)
--       / 用户外部账号绑定(notify_user_binding)
-- 架构：通道 SPI（webhook 群机器人 / dingtalk 企业内部应用 / wecom 企微自建应用）
--       + 事件订阅 bitmask（bit0=待办推送 bit1=审批结果 bit2=预警）；
--       事件由 erp-workflow 在任务生成/审批结果/预警扫描三点 publish Spring 事件，
--       NotifyService 路由到启用通道，失败重试 1 次后降级 wf_message 站内信。
-- 说明：凭据(credentials)按通道类型存 JSON——webhook 存 webhook_url；
--       dingtalk 存 corpid/agentId/appSecret(可选 aesKey+token 回调验签)；
--       wecom 存 corpid/secret/agentId(可选 token+encodingAesKey 回调验签)。
--       接口返回凭据一律脱敏（仅末 4 位可见）。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
-- =====================================================================

-- 1. 通知通道配置（租户隔离：各租户独立配置自己的钉钉/企微/群机器人）
CREATE TABLE IF NOT EXISTS `notify_channel_config` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `channel_type`    VARCHAR(20)   NOT NULL COMMENT '通道类型:webhook群机器人(钉钉/企微通用) dingtalk钉钉企业内部应用 wecom企微自建应用',
    `channel_name`    VARCHAR(60)   NOT NULL COMMENT '通道名称(展示用,如 采购审批钉钉群)',
    `credentials`     TEXT          DEFAULT NULL COMMENT '凭据JSON:webhook={webhookUrl} dingtalk={corpId,agentId,appSecret,aesKey?,token?} wecom={corpId,agentId,secret,token?,encodingAesKey?}',
    `event_mask`      INT           NOT NULL DEFAULT 7 COMMENT '事件订阅bitmask:bit0待办推送(bit=1) bit1审批结果(bit=2) bit2预警(bit=4),7=全部',
    `mobile_base_url` VARCHAR(200)  DEFAULT NULL COMMENT 'ERP移动端基地址(如 http://erp.example.com:5173),推送链接=该地址+/m/approve/{taskId}',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `tenant_id`       BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(平台配置,无组织语义,0)',
    `create_by`       BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_type_status` (`channel_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知通道配置(webhook/钉钉/企微)';

-- 2. 发送日志（只增不改；记录每次外发的内容与结果，管理门户可查）
CREATE TABLE IF NOT EXISTS `notify_send_log` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `channel_id`   BIGINT        DEFAULT NULL COMMENT '通道ID(notify_channel_config.id,测试发送时可能已被删)',
    `channel_type` VARCHAR(20)   NOT NULL COMMENT '通道类型:webhook/dingtalk/wecom',
    `event_type`   VARCHAR(20)   NOT NULL COMMENT '事件类型:TODO待办 APPROVAL审批结果 WARN预警 TEST测试发送',
    `receiver_id`  BIGINT        DEFAULT NULL COMMENT '接收人用户ID(sys_user.id,群机器人无接收人时NULL)',
    `receiver`     VARCHAR(100)  DEFAULT NULL COMMENT '接收人外部标识(webhook域名/钉钉userid/企微userid/手机号)',
    `title`        VARCHAR(200)  NOT NULL COMMENT '消息标题',
    `content`      TEXT          DEFAULT NULL COMMENT '发送的Markdown内容(排查对账用)',
    `success`      TINYINT       NOT NULL DEFAULT 0 COMMENT '发送结果:1成功 0失败(重试1次后仍失败)',
    `error_msg`    VARCHAR(500)  DEFAULT NULL COMMENT '失败原因(截断500)',
    `cost_ms`      BIGINT        DEFAULT NULL COMMENT '耗时毫秒(含重试)',
    `tenant_id`    BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`       BIGINT        NOT NULL DEFAULT 0 COMMENT '组织ID(日志表,无组织语义,0)',
    `create_by`    BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `update_by`    BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(日志表恒0,按建表规范保留)',
    PRIMARY KEY (`id`),
    KEY `idx_channel` (`channel_id`),
    KEY `idx_event` (`event_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知发送日志';

-- 3. 用户外部账号绑定（钉钉/企微通道按此找人；未绑定用户该通道跳过并降级站内信）
CREATE TABLE IF NOT EXISTS `notify_user_binding` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT 'ERP用户ID(sys_user.id)',
    `channel`     VARCHAR(20)  NOT NULL COMMENT '外部通道:dingtalk/wecom',
    `external_id` VARCHAR(64)  NOT NULL COMMENT '外部标识:钉钉=手机号(通道调getbymobile换userid) 企微=成员userid(通常配置为手机号)',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID',
    `org_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(映射表,无组织语义,0)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_channel` (`user_id`, `channel`),
    KEY `idx_channel` (`channel`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户外部账号绑定(钉钉手机号/企微userid)';

-- 4. 菜单与按钮权限码（管理门户 /admin/system/notify-channels，AdminLayout 菜单项已预置）
INSERT IGNORE INTO `sys_menu` (`id`, `org_id`, `tenant_id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(18,  0, 1000, 1,   '通知通道', 2, 'system/notify-channels', 'system/notify/ChannelList', 'system:notify:list', NULL, 8, 1, 1),
(181, 0, 1000, 18,  '通道新增修改', 3, NULL, NULL, 'system:notify:edit',   NULL, 1, 1, 1),
(182, 0, 1000, 18,  '通道删除',     3, NULL, NULL, 'system:notify:delete', NULL, 2, 1, 1),
(183, 0, 1000, 18,  '通道测试发送', 3, NULL, NULL, 'system:notify:test',   NULL, 3, 1, 1);

-- super_admin / management 角色补齐关联（super_admin 代码层全量放行，数据保持完整性）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (18, 181, 182, 183);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 520000510, `id` FROM `sys_menu` WHERE `id` IN (18, 181, 182, 183);
