-- =====================================================================
-- V541 (finance段): 通用红冲 + 单据联查（T7）
-- 1. biz_reverse_log 红冲台账表（原单↔红冲单配对，幂等 CREATE IF NOT EXISTS）；
-- 2. sys_menu 种子：单据联查页面菜单 + 各模块红冲按钮权限（INSERT IGNORE，固定ID）。
-- 注意：种子不写 org_id/tenant_id 列（V1012 已补列并带默认值 0/1000）。
-- =====================================================================

-- ---------- 1. 红冲台账 ----------
CREATE TABLE IF NOT EXISTS `biz_reverse_log` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=总厂)',
    `tenant_id`    BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户)',
    `source_type`  VARCHAR(30)  NOT NULL COMMENT '原单类型(PUR_INSTOCK/SAL_OUTSTOCK/PUR_RETURN/SAL_RETURN/RECEIPT/PAYMENT)',
    `source_id`    BIGINT       NOT NULL COMMENT '原单ID',
    `source_no`    VARCHAR(50)  NOT NULL COMMENT '原单号',
    `reverse_type` VARCHAR(30)  NOT NULL COMMENT '红冲单类型(与原单相同)',
    `reverse_id`   BIGINT       NOT NULL COMMENT '红冲单ID',
    `reverse_no`   VARCHAR(50)  NOT NULL COMMENT '红冲单号(原单号+R)',
    `reason`       VARCHAR(500) DEFAULT NULL COMMENT '红冲原因',
    `reverse_by`   BIGINT       DEFAULT 0 COMMENT '操作人',
    `reverse_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `create_by`    BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reverse_source` (`source_type`, `source_id`),
    KEY `idx_reverse_doc` (`reverse_type`, `reverse_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通用红冲台账(原单↔红冲单配对,V541)';

-- ---------- 2. 菜单与按钮权限种子（固定ID 4801-4816，INSERT IGNORE 幂等） ----------
-- 联查页面挂在 基础资料 目录(200)下；红冲按钮挂各单据菜单下，与 Controller @RequirePerm 同码。
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(4801, 200, '单据联查', 2, 'basedata/doc-chain', 'basedata/DocChain', 'basedata:docChain:list', NULL, 90, 1, 1),
(4811, 212, '入库单红冲', 3, NULL, NULL, 'purchase:instock:reverse', NULL, 11, 1, 1),
(4812, 213, '采购退货红冲', 3, NULL, NULL, 'purchase:return:reverse', NULL, 11, 1, 1),
(4813, 222, '出库单红冲', 3, NULL, NULL, 'sales:outstock:reverse', NULL, 11, 1, 1),
(4814, 223, '销售退货红冲', 3, NULL, NULL, 'sales:return:reverse', NULL, 11, 1, 1),
(4815, 253, '收款单红冲', 3, NULL, NULL, 'finance:receipt:reverse', NULL, 11, 1, 1),
(4816, 254, '付款单红冲', 3, NULL, NULL, 'finance:payment:reverse', NULL, 11, 1, 1);
