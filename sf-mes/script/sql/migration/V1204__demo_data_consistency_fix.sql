-- =====================================================================
-- V1204: V1203 演示数据一致性修正
-- 1. 审批场景改用审批引擎白名单内的 mrp_work_order；
-- 2. 审批节点改为超级管理员角色，避免定义依赖某个同步用户的固定 ID；
-- 3. 按 input-output-scrap 口径修正总装工序 WIP 数量。
-- =====================================================================

UPDATE `exe_wip`
SET `wip_qty` = 73.0000,
    `update_time` = NOW()
WHERE `id` = 12030412
  AND `wo_step_id` = 12030312;

UPDATE `wf_definition`
SET `flow_code` = 'DEMO_WORKORDER_RELEASE',
    `flow_name` = '生产工单下达审批（演示）',
    `biz_type` = 'mrp_work_order',
    `remark` = '用于演示生产工单下达审批链路',
    `update_time` = NOW()
WHERE `id` = 12030801
  AND `flow_code` = 'DEMO_QUALITY_EXCEPTION';

UPDATE `wf_node`
SET `node_name` = CASE `node_order`
        WHEN 1 THEN '生产主管审批'
        WHEN 2 THEN '工厂经理确认'
        ELSE `node_name`
    END,
    `approver_type` = 'APPROVER',
    `approver_id` = 1,
    `condition_expr` = CASE WHEN `node_order` = 2 THEN 'qty >= 80' ELSE NULL END,
    `update_time` = NOW()
WHERE `definition_id` = 12030801;

UPDATE `wf_instance`
SET `biz_type` = 'mrp_work_order',
    `biz_id` = 12030305,
    `biz_no` = 'DEMO-WO-005',
    `vars` = '{"qty":100,"material":"精密减速机","action":"RELEASE"}',
    `update_time` = NOW()
WHERE `id` = 12030801
  AND `definition_id` = 12030801;

UPDATE `wf_task`
SET `node_name` = CASE `node_order`
        WHEN 1 THEN '生产主管审批'
        WHEN 2 THEN '工厂经理确认'
        ELSE `node_name`
    END,
    `update_time` = NOW()
WHERE `instance_id` = 12030801;

-- 保留统一认证同步用户（13）的待办，同时为首次启动必然存在的本地管理员（1）生成同节点待办。
-- 该节点为角色或签，任意一人处理后，其他同节点任务由流程引擎自动取消。
INSERT IGNORE INTO `wf_task`
(`id`, `org_id`, `tenant_id`, `instance_id`, `node_id`, `node_order`, `node_name`, `approver_id`, `status`,
 `opinion`, `approve_time`, `create_by`, `create_time`, `deleted`)
VALUES
(12030803, 0, 1000, 12030801, 12030802, 2, '工厂经理确认', 1, 'PENDING',
 NULL, NULL, 11, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0);

UPDATE `wf_message`
SET `title` = '待确认：生产工单下达',
    `content` = 'DEMO-WO-005 已完成生产主管审批，请确认下达。',
    `update_time` = NOW()
WHERE `id` = 12030801;

INSERT IGNORE INTO `wf_message`
(`id`, `org_id`, `tenant_id`, `receiver_id`, `msg_type`, `title`, `content`, `biz_path`, `read_flag`,
 `read_time`, `create_by`, `create_time`, `deleted`)
VALUES
(12030802, 0, 1000, 1, 'TODO', '待确认：生产工单下达', 'DEMO-WO-005 已完成生产主管审批，请确认下达。',
 '/workflow/tasks', 0, NULL, 11, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0);
