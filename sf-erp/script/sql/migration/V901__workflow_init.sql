-- =====================================================================
-- V901: 审批工作流模块(erp-workflow)初始表
-- 范围：流程定义(wf_definition + wf_node 节点子表) / 流程实例(wf_instance
--       + wf_task 审批任务) / 消息中心(wf_message)
-- 约定：所有表统一带 id/create_by/create_time/update_by/update_time/deleted；
--       状态列用 VARCHAR 枚举并配 COMMENT 注明取值；高频查询条件加索引。
-- 说明：轻量审批流引擎（对标金蝶审批流）：
--       1) 同一单据类型可有多个流程定义，仅"启用"状态的参与匹配（取最新一条）；
--       2) 节点按 node_order 串行流转；条件表达式(如 amount>10000)不满足则跳过该节点；
--       3) 审批人类型 APPROVER=按角色(或签，任一成员审批即过) / USER=指定用户；
--       4) 流程实例冗余 vars(JSON) 保存发起时的业务变量，供后续节点条件求值。
-- 另：补充 wf_biz_type 字典（适用单据类型），供前端下拉与展示。
-- =====================================================================

-- 1. 流程定义头
CREATE TABLE IF NOT EXISTS `wf_definition` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `flow_code`   VARCHAR(40)  NOT NULL COMMENT '流程编码(唯一,如 FLOW-PO-001)',
    `flow_name`   VARCHAR(60)  NOT NULL COMMENT '流程名称',
    `biz_type`    VARCHAR(30)  NOT NULL COMMENT '适用单据类型(dict:wf_biz_type):pur_order采购订单/sal_order销售订单/pur_instock采购入库/sal_outstock销售出库/mrp_work_order生产订单/fin_payment付款单/fin_receipt收款单',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用(停用后新单据不再匹配本流程,在途实例不受影响)',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_code` (`flow_code`),
    KEY `idx_biz_type_status` (`biz_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批流程定义头';

-- 2. 流程节点（顺序串行；条件不满足自动跳过）
CREATE TABLE IF NOT EXISTS `wf_node` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `definition_id`  BIGINT       NOT NULL COMMENT '流程定义ID(wf_definition.id)',
    `node_order`     INT          NOT NULL COMMENT '节点序号(从1开始,按序串行流转)',
    `node_name`      VARCHAR(60)  NOT NULL COMMENT '节点名称(如 部门经理审批)',
    `approver_type`  VARCHAR(20)  NOT NULL COMMENT '审批人类型:APPROVER按角色(或签) USER指定用户',
    `approver_id`    BIGINT       NOT NULL COMMENT '审批人ID:APPROVER时为角色ID(sys_role.id) USER时为用户ID(sys_user.id)',
    `condition_expr` VARCHAR(200) DEFAULT NULL COMMENT '条件表达式(可选,如 amount>10000 满足才走本节点否则跳过;变量取自发起时的vars)',
    `create_by`      BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_definition` (`definition_id`, `node_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批流程节点';

-- 3. 流程实例（一张业务单据发起一次）
CREATE TABLE IF NOT EXISTS `wf_instance` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `definition_id`  BIGINT      NOT NULL COMMENT '流程定义ID(wf_definition.id)',
    `biz_type`       VARCHAR(30) NOT NULL COMMENT '单据类型(同wf_definition.biz_type冗余,便于按单据查询)',
    `biz_id`         BIGINT      NOT NULL COMMENT '业务单据ID(如 pur_order.id)',
    `biz_no`         VARCHAR(40) NOT NULL COMMENT '业务单号(如 PO202609050001,展示用)',
    `status`         VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态:RUNNING审批中 AGREED已同意 REJECTED已驳回 CANCELLED已撤回',
    `start_user_id`  BIGINT      NOT NULL COMMENT '发起人用户ID(sys_user.id)',
    `vars`           TEXT        DEFAULT NULL COMMENT '发起时的业务变量(JSON,如 {"amount":8000},供节点条件求值)',
    `end_time`       DATETIME    DEFAULT NULL COMMENT '结束时间(同意/驳回/撤回时间,运行中为空)',
    `create_by`      BIGINT      DEFAULT 0 COMMENT '创建人',
    `create_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(即发起时间)',
    `update_by`      BIGINT      DEFAULT 0 COMMENT '更新人',
    `update_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_status` (`status`),
    KEY `idx_start_user` (`start_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批流程实例';

-- 4. 审批任务（待办/已办；角色节点按成员一人一条,或签）
CREATE TABLE IF NOT EXISTS `wf_task` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `instance_id`  BIGINT       NOT NULL COMMENT '流程实例ID(wf_instance.id)',
    `node_id`      BIGINT       NOT NULL COMMENT '节点ID(wf_node.id,定义侧引用)',
    `node_order`   INT          NOT NULL COMMENT '节点序号(冗余快照,防定义修改影响历史任务展示)',
    `node_name`    VARCHAR(60)  NOT NULL COMMENT '节点名称(冗余快照)',
    `approver_id`  BIGINT       NOT NULL COMMENT '审批人用户ID(sys_user.id)',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待审批 APPROVED已通过 REJECTED已驳回 CANCELLED已失效(同节点他人先处理/实例结束)',
    `opinion`      VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    `approve_time` DATETIME     DEFAULT NULL COMMENT '审批时间',
    `create_by`    BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(即任务到达时间)',
    `update_by`    BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_instance` (`instance_id`),
    KEY `idx_approver_status` (`approver_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批任务(待办已办)';

-- 5. 消息中心（审批通知 + 预警）
CREATE TABLE IF NOT EXISTS `wf_message` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `receiver_id` BIGINT       NOT NULL COMMENT '接收人用户ID(sys_user.id)',
    `msg_type`    VARCHAR(20)  NOT NULL COMMENT '消息类型:TODO新待办 APPROVE审批通过 REJECT审批驳回 WARN预警',
    `title`       VARCHAR(120) NOT NULL COMMENT '标题',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '内容',
    `biz_path`    VARCHAR(200) DEFAULT NULL COMMENT '关联前端路径(点击跳转,如 /workflow/todo)',
    `read_flag`   TINYINT      NOT NULL DEFAULT 0 COMMENT '已读标志:0未读 1已读',
    `read_time`   DATETIME     DEFAULT NULL COMMENT '已读时间',
    `create_by`   BIGINT       DEFAULT 0 COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT 0 COMMENT '更新人',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0在用 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_receiver_read` (`receiver_id`, `read_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息中心(审批通知/预警)';

-- 6. 补充字典：适用单据类型 wf_biz_type（不指定 id,避免与并行模块字典冲突）
INSERT INTO `sys_dict_type` (`name`, `type`, `status`, `remark`)
VALUES ('审批单据类型', 'wf_biz_type', 1, '审批流适用单据类型(工作流模块)');

INSERT INTO `sys_dict_data` (`dict_type`, `label`, `value`, `sort`, `status`) VALUES
('wf_biz_type', '采购订单',   'pur_order',      1, 1),
('wf_biz_type', '销售订单',   'sal_order',      2, 1),
('wf_biz_type', '采购入库单', 'pur_instock',    3, 1),
('wf_biz_type', '销售出库单', 'sal_outstock',   4, 1),
('wf_biz_type', '生产订单',   'mrp_work_order', 5, 1),
('wf_biz_type', '付款单',     'fin_payment',    6, 1),
('wf_biz_type', '收款单',     'fin_receipt',    7, 1);
