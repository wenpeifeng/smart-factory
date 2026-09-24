-- =====================================================================
-- V531: 财务三大报表 —— 取数映射配置表 fin_report_mapping + 种子
-- 背景：
--   资产负债表 / 利润表从科目余额（fin_voucher POSTED 凭证分录聚合）取数，
--   行项目 ↔ 科目编码的对应关系做成配置表（而非硬编码），后续科目体系
--   变化只需 UPDATE 本表即可，报表引擎完全按映射驱动。
-- 表设计：
--   * report_type  报表类型：BALANCE_SHEET 资产负债表 / INCOME_STATEMENT 利润表；
--   * side         行项目所属侧：资产负债表 ASSET 资产 / LIABILITY 负债 /
--                  EQUITY 权益（决定计入哪一侧合计）；利润表留空（direction 已表达损益方向）；
--   * item_name    行项目名称（含"减："/"一、"等展示前缀）；
--   * subject_codes 科目编码集合（逗号分隔，前缀匹配：2221 同时命中 2221 与 222101；
--                  空串 = 无对应科目，行显示 0，如"税金及附加/在建工程"）；
--   * direction    取数方向：DEBIT 借方净额(借-贷) / CREDIT 贷方净额(贷-借)；
--                  资产侧行（含累计折旧/坏账准备等减项，贷方余额自然为负数）用 DEBIT，
--                  负债权益侧行与收入行用 CREDIT，成本费用行用 DEBIT；
--   * calc_type    计算行类型：空=普通取数行；ASSET_TOTAL 资产总计 /
--                  LIAB_TOTAL 负债合计 / EQUITY_TOTAL 所有者权益合计 /
--                  LIAB_EQ_TOTAL 负债和所有者权益总计（=两侧数据行运行合计）/
--                  OPERATING_PROFIT 营业利润 / TOTAL_PROFIT 利润总额 /
--                  NET_PROFIT 净利润（=上方全部数据行 贷-借 运行合计）；
--   * include_pl   1=该行并入当期损益净额（未分配利润行：4103+4104 余额 +
--                  损益类科目未结转净额，实现"资产=负债+权益"勾稽）；
--   * book_id      0=全账套通用（映射按科目编码对齐，账套间复制科目树后同样生效）。
-- 口径说明（种子映射）：
--   * 存货 = 1403 原材料 + 1405 库存商品 + 5001 生产成本 + 5101 制造费用
--     （生产成本/制造费用即"在产品"，借方余额并入存货，否则报表必不平衡）；
--   * 营业收入 = 6001 + 6051，营业成本 = 6401 + 6402（主营业务+其他业务合并列示）；
--   * 种子科目无 税金及附加(6403)/在建工程(1604)，行保留、编码留空，金额恒为 0。
-- 幂等：建表 IF NOT EXISTS；种子按 (report_type,item_name) 存在性判断逐行插入，
--       重复执行无害（并行任务若同段落加列亦互不冲突）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `fin_report_mapping`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户ID(默认1000=默认租户,与V1012对齐)',
    `org_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '组织ID(0=租户级共享配置,与V1012对齐)',
    `book_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '账套ID(0=全账套通用,映射按科目编码对齐)',
    `report_type`   VARCHAR(30)  NOT NULL COMMENT '报表类型:BALANCE_SHEET资产负债表 INCOME_STATEMENT利润表',
    `side`          VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '行项目所属侧:ASSET资产 LIABILITY负债 EQUITY权益(利润表留空)',
    `item_name`     VARCHAR(60)  NOT NULL COMMENT '行项目名称(含 减：/一、 等展示前缀)',
    `subject_codes` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '取数科目编码集合(逗号分隔,前缀匹配,空=无科目行显示0)',
    `direction`     VARCHAR(10)  NOT NULL COMMENT '取数方向:DEBIT借方净额(借-贷) CREDIT贷方净额(贷-借)',
    `calc_type`     VARCHAR(30)  NOT NULL DEFAULT '' COMMENT '计算行类型:空=取数行 ASSET_TOTAL/LIAB_TOTAL/EQUITY_TOTAL/LIAB_EQ_TOTAL/OPERATING_PROFIT/TOTAL_PROFIT/NET_PROFIT',
    `include_pl`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否并入当期损益净额:1是(未分配利润行) 0否',
    `sort_no`       INT          NOT NULL COMMENT '行序(小在前,展示与运行合计顺序)',
    `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注(口径说明)',
    `create_by`     BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_name` (`report_type`, `item_name`),
    KEY `idx_report_type` (`report_type`),
    KEY `idx_book_id` (`book_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '财务报表取数映射配置(资产负债表/利润表行项目-科目编码)';

-- ---------------------------------------------------------------------
-- 种子 1：资产负债表行项目映射（资产侧 direction=DEBIT：借-贷，减项贷方余额自然为负）
-- ---------------------------------------------------------------------
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '货币资金', '1001,1002,1012', 'DEBIT', '', 0, 10, '库存现金+银行存款+其他货币资金'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '货币资金');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '应收账款', '1122', 'DEBIT', '', 0, 20, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '应收账款');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '预付款项', '1123', 'DEBIT', '', 0, 30, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '预付款项');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '其他应收款', '1221', 'DEBIT', '', 0, 40, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '其他应收款');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '存货', '1403,1405,5001,5101', 'DEBIT', '', 0, 50, '原材料+库存商品+生产成本/制造费用(在产品)'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '存货');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '固定资产', '1601', 'DEBIT', '', 0, 60, '固定资产原值'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '固定资产');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '减：累计折旧', '1602', 'DEBIT', '', 0, 70, '贷方余额，按借-贷取数自然为负数(减项)'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '减：累计折旧');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '减：坏账准备', '1231', 'DEBIT', '', 0, 80, '贷方余额，按借-贷取数自然为负数(减项)'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '减：坏账准备');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '固定资产清理', '1606', 'DEBIT', '', 0, 90, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '固定资产清理');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '在建工程', '', 'DEBIT', '', 0, 100, '种子科目无1604，行保留显示0'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '在建工程');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'ASSET', '资产总计', '', 'DEBIT', 'ASSET_TOTAL', 0, 110, '=资产侧全部取数行合计'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '资产总计');

-- ---------------------------------------------------------------------
-- 种子 2：资产负债表负债+权益侧行项目（direction=CREDIT：贷-借）
-- ---------------------------------------------------------------------
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '短期借款', '2001', 'CREDIT', '', 0, 210, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '短期借款');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '应付账款', '2202', 'CREDIT', '', 0, 220, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '应付账款');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '预收款项', '2203', 'CREDIT', '', 0, 230, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '预收款项');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '应付职工薪酬', '2211', 'CREDIT', '', 0, 240, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '应付职工薪酬');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '应交税费', '2221', 'CREDIT', '', 0, 250, '前缀匹配：2221+222101应交增值税'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '应交税费');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '长期借款', '2501', 'CREDIT', '', 0, 260, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '长期借款');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '负债合计', '', 'CREDIT', 'LIAB_TOTAL', 0, 270, '=负债侧全部取数行合计'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '负债合计');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'EQUITY', '实收资本', '4001', 'CREDIT', '', 0, 310, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '实收资本');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'EQUITY', '资本公积', '4002', 'CREDIT', '', 0, 320, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '资本公积');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'EQUITY', '未分配利润', '4103,4104', 'CREDIT', '', 1, 330, '本年利润+利润分配余额+当期损益净额(未结转损益并入，保证资产=负债+权益)'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '未分配利润');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'EQUITY', '所有者权益合计', '', 'CREDIT', 'EQUITY_TOTAL', 0, 340, '=权益侧全部取数行合计'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '所有者权益合计');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'BALANCE_SHEET', 'LIABILITY', '负债和所有者权益总计', '', 'CREDIT', 'LIAB_EQ_TOTAL', 0, 350, '=负债合计+所有者权益合计，与资产总计勾稽'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'BALANCE_SHEET' AND `item_name` = '负债和所有者权益总计');

-- ---------------------------------------------------------------------
-- 种子 3：利润表行项目映射（收入类 CREDIT=贷-借，成本费用类 DEBIT=借-贷；
--          营业利润/利润总额/净利润 = 上方数据行 贷-借 运行合计）
-- ---------------------------------------------------------------------
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '一、营业收入', '6001,6051', 'CREDIT', '', 0, 10, '主营业务收入+其他业务收入'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '一、营业收入');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '减：营业成本', '6401,6402', 'DEBIT', '', 0, 20, '主营业务成本+其他业务成本'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '减：营业成本');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '税金及附加', '', 'DEBIT', '', 0, 30, '种子科目无6403，行保留显示0'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '税金及附加');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '销售费用', '6601', 'DEBIT', '', 0, 40, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '销售费用');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '管理费用', '6602', 'DEBIT', '', 0, 50, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '管理费用');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '财务费用', '6603', 'DEBIT', '', 0, 60, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '财务费用');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '加：投资收益', '6111', 'CREDIT', '', 0, 70, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '加：投资收益');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '二、营业利润', '', 'CREDIT', 'OPERATING_PROFIT', 0, 80, '=上方数据行(贷-借)运行合计'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '二、营业利润');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '加：营业外收入', '6301', 'CREDIT', '', 0, 90, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '加：营业外收入');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '减：营业外支出', '6711', 'DEBIT', '', 0, 100, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '减：营业外支出');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '三、利润总额', '', 'CREDIT', 'TOTAL_PROFIT', 0, 110, '=上方数据行(贷-借)运行合计'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '三、利润总额');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '减：所得税费用', '6801', 'DEBIT', '', 0, 120, NULL
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '减：所得税费用');
INSERT INTO `fin_report_mapping`
(`report_type`, `side`, `item_name`, `subject_codes`, `direction`, `calc_type`, `include_pl`, `sort_no`, `remark`)
SELECT 'INCOME_STATEMENT', '', '四、净利润', '', 'CREDIT', 'NET_PROFIT', 0, 130, '=全部数据行(贷-借)合计，应等于未分配利润中的当期损益净额'
WHERE NOT EXISTS (SELECT 1 FROM `fin_report_mapping` WHERE `report_type` = 'INCOME_STATEMENT' AND `item_name` = '四、净利润');

-- ---------------------------------------------------------------------
-- 种子 4：权限码（三大报表只读查询，挂财务管理目录下，与 V1101 行同款；
--          前端入口在报表中心财务分组，sys_menu 行仅服务鉴权）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 3551, 250, '财务三大报表', 2, 'report/center', 'report/center/ReportCenter', 'finance:report:list', NULL, 10, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 3551 OR `perms` = 'finance:report:list');
