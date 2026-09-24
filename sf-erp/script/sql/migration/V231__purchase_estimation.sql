-- =====================================================================
-- V231: 采购暂估入库（货到票未到）—— 暂估应付主表 + 菜单权限（V23x 段）
-- 对标金蝶：采购入库当月发票未到 -> 按订单价（不含税）暂估应付；
--           票到后红冲暂估 + 按发票实际金额入正式应付 + 差额调整行。
-- 内容：
--   1) pur_estimation 暂估应付单（一入库单一条，跟踪冲回进度）
--   2) 采购管理目录(210)下新增「暂估应付」菜单(217)与按钮权限码：
--      purchase:estimation:{list,edit,audit}
--      （edit=手工生成暂估 / audit=暂估冲回）
-- 联动口径（详见 docs/modules/purchase.md 暂估章节）：
--   * 暂估金额 = Σ 明细数量 × 含税单价 ÷ (1+税率%) —— 不含税净额，HALF_UP 2 位；
--   * 暂估生成时向 fin_payable INSERT 一条（biz_no=ZG 单号，remark=暂估应付）；
--   * 冲回时向 fin_payable INSERT 红字行/正式行/差额行（biz_no 前缀 ZG 者属暂估链路），
--     与并行任务凭证引擎的边界：FIN_PAYABLE 来源 biz_no 前缀 ZG 的行由暂估链路解释。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 暂估应付单主表
-- 状态机: 0暂估中 -> 10已冲回(终态,全额冲回) / 20部分冲回(发票未全额覆盖)
--         0/20 可继续冲回(新发票到达)；10 拒绝再冲回。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pur_estimation` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `org_id`               BIGINT        NOT NULL DEFAULT 1 COMMENT '组织ID(org_factory.id,1=本部)',
    `tenant_id`            BIGINT        NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=演示租户)',
    `estimation_no`        VARCHAR(30)   NOT NULL COMMENT '暂估单号(ZG+yyyyMMdd+3位流水,Redis生成)',
    `instock_id`           BIGINT        NOT NULL COMMENT '采购入库单ID(pur_instock.id)',
    `instock_no`           VARCHAR(30)   NOT NULL COMMENT '采购入库单号(RK...,冗余便于报表检索)',
    `supplier_id`          BIGINT        NOT NULL COMMENT '供应商ID(base_supplier.id)',
    `amount`               DECIMAL(18,2) NOT NULL COMMENT '暂估金额(不含税)=Σ qty×price÷(1+税率%)',
    `reversed_amount`      DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '累计已冲回金额(<=amount)',
    `status`               TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0暂估中 10已冲回 20部分冲回',
    `period`               VARCHAR(6)    NOT NULL COMMENT '暂估期间 yyyyMM(按入库单审核时点入库月份)',
    `invoice_no`           VARCHAR(20)   DEFAULT NULL COMMENT '最近一次冲回所用发票号(未冲回为NULL)',
    `reversed_invoice_nos` VARCHAR(500)  DEFAULT NULL COMMENT '已冲回所用发票号清单(逗号分隔,防同一发票重复冲回)',
    `remark`               VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`            BIGINT        DEFAULT 0 COMMENT '创建人',
    `create_time`          DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            BIGINT        DEFAULT 0 COMMENT '更新人',
    `update_time`          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_estimation_no` (`estimation_no`),
    UNIQUE KEY `uk_estimation_instock` (`instock_id`, `deleted`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_period` (`period`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购暂估应付单(货到票未到)';

-- ---------------------------------------------------------------------
-- 2. 菜单与按钮权限（挂采购管理目录 210 下，前端路由/菜单文案由主线程协调）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES
(217, 210, '暂估应付', 2, 'purchase/estimations', 'purchase/estimations/EstimationList', 'purchase:estimation:list', NULL, 6, 1, 1),
(2171, 217, '暂估手工生成', 3, NULL, NULL, 'purchase:estimation:edit',   NULL, 1, 1, 1),
(2172, 217, '暂估冲回',     3, NULL, NULL, 'purchase:estimation:audit',  NULL, 2, 1, 1);

-- super_admin(role_id=1) 补齐新菜单关联（代码层对该角色全量放行，此处保持数据完整性）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (217, 2171, 2172);
