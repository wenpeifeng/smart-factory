-- =====================================================================
-- V1042: 用户-组织绑定表（docs/集团组织与数据权限设计.md 第 7 章遗留 4）
-- 目的：
--   1) 新建 sys_user_org(user_id, org_id, uk(user_id, org_id))：
--      登录/切换组织的"可切换组织列表"数据源 —— 有绑定的用户仅见绑定的启用组织，
--      无绑定（含超管/存量用户）回退为全部启用组织（兼容现状，防越权收紧）；
--   2) 平台维护接口 GET/PUT /system/users/{id}/orgs（SysUserController，整体覆盖）。
-- 说明：
--   * 绑定行按"整体覆盖"语义维护（重分配先物理 DELETE 再 INSERT，见
--     SysUserOrgMapper.xml），逻辑删除会在 uk(user_id, org_id) 上撞键，故不使用；
--   * 按新表规范带 tenant_id（租户插件自动追加条件/补列）与 org_id；
--     本表 org_id 即绑定目标组织列，一身二职；
--   * 幂等：CREATE TABLE IF NOT EXISTS。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_user_org`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID(sys_user.id)',
    `org_id`      BIGINT       NOT NULL COMMENT '组织ID(org_factory.id,绑定的可切换组织)',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删(绑定行整体覆盖维护,恒为0)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_org` (`user_id`, `org_id`),
    KEY `idx_user_org_org` (`org_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-组织绑定(可切换组织来源,空=全部启用组织)';
