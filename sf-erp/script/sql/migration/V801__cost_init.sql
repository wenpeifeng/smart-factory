-- =====================================================================
-- V801 成本管理模块(erp-cost)初始化
-- 目的：补齐金蝶级「存货核算 + 生产成本」能力，建 3 张自有表：
--   1) cost_flow        存货计价流水（移动加权平均法运行台账，正入负出）
--   2) cost_period      存货核算期间（月末结账状态机 OPEN → CLOSED）
--   3) cost_work_order  工单成本归集（制造费用/工时手工分摊录入，
--                       料/工从 inv_movement 计价流水与 hr 约定表实时归集）
-- 跨模块约定表（只读/按约定列 INSERT，不建物理外键、不 import 对方代码）：
--   inv_movement / mrp_work_order / mrp_material_issue(+item) /
--   pur_instock(+detail) / fin_voucher / fin_voucher_entry / fin_subject /
--   hr_piece_wage_detail(HR 二期并行开发中，未建成前优雅降级为 0)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 存货计价流水（移动加权平均）
-- 运行余额(balance_qty/balance_cost) = 本行过账后的物料结存，
-- 按物料全仓维度加权（与 inv_stock 仓库维度互补，仓库列仅作溯源）。
-- ---------------------------------------------------------------------
CREATE TABLE `cost_flow`
(
    `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `biz_type`     VARCHAR(20)    NOT NULL COMMENT '业务类型:PUR_IN采购入库/PUR_RET采购退货/SAL_OUT销售出库/SAL_RET销售退货/OTH_IN其他入库/OTH_OUT其他出库/ADJUST盘点调整/MOVE调拨/PROD_IN生产入库/PROD_USE生产领料',
    `biz_id`       BIGINT         DEFAULT NULL COMMENT '来源单据ID(如 pur_instock.id/mrp_material_issue.id)',
    `biz_no`       VARCHAR(40)    DEFAULT NULL COMMENT '来源单号(RK.../XS.../MI...等,手工补录可自定义)',
    `movement_id`  BIGINT         DEFAULT NULL COMMENT '关联库存流水ID(inv_movement.id,cost/sync重算时回填;手工补录为空)',
    `material_id`  BIGINT         NOT NULL COMMENT '物料ID(base_material.id)',
    `warehouse_id` BIGINT         DEFAULT NULL COMMENT '仓库ID(base_material.id,计价按物料全仓加权,此列仅溯源)',
    `biz_date`     DATE           DEFAULT NULL COMMENT '业务日期(期间period的取值依据,空=入账当天)',
    `qty`          DECIMAL(18, 4) NOT NULL COMMENT '数量(正入负出)',
    `unit_cost`    DECIMAL(18, 6) NOT NULL DEFAULT '0.000000' COMMENT '单价(入库行=本次入库单价,出库行=当时移动加权价,恒为非负,6位小数)',
    `amount`       DECIMAL(18, 2) NOT NULL COMMENT '金额(与qty同号:入库为正,出库为负)',
    `balance_qty`  DECIMAL(18, 4) NOT NULL COMMENT '运行余额:本行过账后结存数量',
    `balance_cost` DECIMAL(18, 2) NOT NULL COMMENT '运行余额:本行过账后结存金额',
    `period`       VARCHAR(6)     NOT NULL COMMENT '期间(yyyyMM,取业务日期所在月)',
    `create_by`    BIGINT         DEFAULT '0' COMMENT '创建人',
    `create_time`  DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    BIGINT         DEFAULT '0' COMMENT '更新人',
    `update_time`  DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT        NOT NULL DEFAULT '0' COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_flow_material` (`material_id`, `id`),
    KEY `idx_flow_period` (`period`),
    KEY `idx_flow_biz` (`biz_type`, `biz_no`),
    KEY `idx_flow_movement` (`movement_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '存货计价流水(移动加权平均法运行台账)';

-- ---------------------------------------------------------------------
-- 2. 存货核算期间（月末结账）
-- status 单向流转 OPEN → CLOSED；结账时自动生成转账凭证并快照出库成本。
-- ---------------------------------------------------------------------
CREATE TABLE `cost_period`
(
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `period`      VARCHAR(6)    NOT NULL COMMENT '期间(yyyyMM)',
    `status`      VARCHAR(10)   NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN打开(可过账/可重算) CLOSED已结账(禁止过账)',
    `out_amount`  DECIMAL(18,2) NOT NULL DEFAULT '0.00' COMMENT '本期出库成本合计(结账时快照,负数列示为正)',
    `voucher_id`  BIGINT        DEFAULT NULL COMMENT '结账生成的转账凭证ID(fin_voucher.id,无凭证为空)',
    `voucher_no`  VARCHAR(40)   DEFAULT NULL COMMENT '结账生成的转账凭证号',
    `settle_by`   BIGINT        DEFAULT NULL COMMENT '结账人(用户ID,0=系统)',
    `settle_time` DATETIME      DEFAULT NULL COMMENT '结账时间',
    `remark`      VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT        DEFAULT '0' COMMENT '创建人',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT        DEFAULT '0' COMMENT '更新人',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT '0' COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cost_period` (`period`, `deleted`),
    KEY `idx_cp_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '存货核算期间(月末结账状态)';

-- ---------------------------------------------------------------------
-- 3. 工单成本归集（料工费中的「费」手工分摊 + 工单快照）
-- 料 = mrp_material_issue(item) 领料量 × 移动加权单价（实时归集）；
-- 工 = hr_piece_wage_detail(work_order_id, employee_id, amount) 计件工资（HR 约定表）；
-- 费 = 本表 overhead_amount/labor_hours 手工录入。
-- ---------------------------------------------------------------------
CREATE TABLE `cost_work_order`
(
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `work_order_id`   BIGINT        NOT NULL COMMENT '生产订单ID(mrp_work_order.id)',
    `order_no`        VARCHAR(30)   NOT NULL COMMENT '生产订单号快照(MO...)',
    `material_id`     BIGINT        NOT NULL COMMENT '成品物料ID(base_material.id)',
    `qty`             DECIMAL(18,4) NOT NULL DEFAULT '0.0000' COMMENT '订单数量快照',
    `finish_qty`      DECIMAL(18,4) NOT NULL DEFAULT '0.0000' COMMENT '完工数量快照',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '订单状态快照(DRAFT/RELEASED/PRODUCING/FINISHED/CLOSED)',
    `labor_hours`     DECIMAL(18,4) NOT NULL DEFAULT '0.0000' COMMENT '工时(小时,制造费用分摊依据,手工录入)',
    `overhead_amount` DECIMAL(18,2) NOT NULL DEFAULT '0.00' COMMENT '制造费用分摊金额(手工录入)',
    `remark`          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    `create_by`       BIGINT        DEFAULT '0' COMMENT '创建人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT        DEFAULT '0' COMMENT '更新人',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT '0' COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cwo_work_order` (`work_order_id`, `deleted`),
    KEY `idx_cwo_material` (`material_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单成本归集(制造费用/工时手工分摊+订单快照)';
