package com.erp.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.workflow.constant.WfConstants;
import com.erp.workflow.dto.query.WfInstanceQuery;
import com.erp.workflow.dto.query.WfTaskQuery;
import com.erp.workflow.dto.vo.StartResultVO;
import com.erp.workflow.dto.vo.WfInstanceVO;
import com.erp.workflow.dto.vo.WfTaskVO;
import com.erp.workflow.entity.WfDefinition;
import com.erp.workflow.entity.WfInstance;
import com.erp.workflow.entity.WfMessage;
import com.erp.workflow.entity.WfNode;
import com.erp.workflow.entity.WfTask;
import com.erp.workflow.mapper.WfDefinitionMapper;
import com.erp.workflow.mapper.WfInstanceMapper;
import com.erp.workflow.mapper.WfNodeMapper;
import com.erp.workflow.mapper.WfRefMapper;
import com.erp.workflow.mapper.WfTaskMapper;
import com.erp.workflow.security.CurrentUser;
import com.erp.workflow.service.WfMessageService;
import com.erp.workflow.service.WorkflowService;
import com.erp.workflow.support.ConditionEvaluator;
import com.erp.notify.event.NotifyEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批引擎核心实现（轻量串行流，对标金蝶"多级审批+条件跳转"）。
 * <p>
 * 关键设计：
 * <ul>
 *   <li><b>条件路由</b>：节点串行推进，conditionExpr（如 amount&gt;10000）对发起时的
 *       vars 求值，不满足即跳过；全跳过则实例即时 AGREED；</li>
 *   <li><b>或签</b>：角色节点对角色下全部启用用户各派一条待办，任一成员处理后，
 *       同节点其余 PENDING 任务置 CANCELLED（条件 UPDATE 防并发双处理）；</li>
 *   <li><b>幂等</b>：同一单据已有 RUNNING 实例时重复 startFlow 返回原实例；</li>
 *   <li><b>快照</b>：任务冗余节点序号/名称；实例冗余 vars JSON——定义后续修改
 *       不影响在途流程的展示与走向判断；</li>
 *   <li><b>并发防护</b>：任务状态推进用条件 UPDATE（WHERE status='PENDING'），
 *       影响行数=0 即"已被他人处理"，整体回滚（与采购审核同一防重口径）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WfDefinitionMapper definitionMapper;
    private final WfNodeMapper nodeMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final WfRefMapper refMapper;
    private final WfMessageService messageService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartResultVO startFlow(String bizType, Long bizId, String bizNo, Map<String, Object> vars) {
        if (!WfConstants.BIZ_TYPES.contains(bizType)) {
            throw BusinessException.ofKey("erp.workflow.biztype-not-supported", bizType, WfConstants.BIZ_TYPES);
        }
        Long userId = CurrentUser.requireUserId();

        // 幂等：同单据已有运行中实例直接返回（业务单据重复点"提交审核"场景）
        WfInstance existing = instanceMapper.selectOne(Wrappers.lambdaQuery(WfInstance.class)
                .eq(WfInstance::getBizType, bizType)
                .eq(WfInstance::getBizId, bizId)
                .eq(WfInstance::getStatus, WfInstance.STATUS_RUNNING)
                .orderByDesc(WfInstance::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return StartResultVO.running(existing.getId());
        }

        // 匹配启用流程：同类型多条时取最新启用的一条
        WfDefinition definition = definitionMapper.selectOne(Wrappers.lambdaQuery(WfDefinition.class)
                .eq(WfDefinition::getBizType, bizType)
                .eq(WfDefinition::getStatus, WfDefinition.STATUS_ENABLED)
                .orderByDesc(WfDefinition::getId)
                .last("LIMIT 1"));
        if (definition == null) {
            // 无启用流程：单据免审直接过（调用方据此走原审核逻辑）
            return StartResultVO.skipped();
        }
        List<WfNode> nodes = orderedNodes(definition.getId());
        if (nodes.isEmpty()) {
            log.warn("流程定义[{}]无可用节点，按无流程处理(单据直接过)", definition.getId());
            return StartResultVO.skipped();
        }

        Map<String, Object> safeVars = vars == null ? new HashMap<>() : vars;
        WfInstance instance = new WfInstance();
        instance.setDefinitionId(definition.getId());
        instance.setBizType(bizType);
        instance.setBizId(bizId);
        instance.setBizNo(bizNo);
        instance.setStatus(WfInstance.STATUS_RUNNING);
        instance.setStartUserId(userId);
        instance.setVars(writeVars(safeVars));
        instanceMapper.insert(instance);

        // 首个满足条件的节点；全不满足则即时通过
        WfNode first = nextActiveNode(nodes, 0, safeVars);
        if (first == null) {
            finishInstance(instance, WfInstance.STATUS_AGREED);
            return StartResultVO.agreed(instance.getId());
        }
        dispatchNode(instance, definition.getFlowName(), first, safeVars);
        return StartResultVO.running(instance.getId());
    }

    @Override
    public boolean isApproved(String bizType, Long bizId) {
        WfInstance latest = instanceMapper.selectOne(Wrappers.lambdaQuery(WfInstance.class)
                .eq(WfInstance::getBizType, bizType)
                .eq(WfInstance::getBizId, bizId)
                .orderByDesc(WfInstance::getId)
                .last("LIMIT 1"));
        // 无实例 = 未接流程/无匹配流程，视为通过（一期单据接入时的兼容语义）
        return latest == null || WfInstance.STATUS_AGREED.equals(latest.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, String opinion) {
        WfTask task = requireMyPendingTask(taskId);
        WfInstance instance = requireRunningInstance(task.getInstanceId());
        WfDefinition definition = definitionMapper.selectById(instance.getDefinitionId());

        // 条件 UPDATE 占坑：并发下同节点两人同时提交，只允许第一个成功
        int rows = taskMapper.update(null, Wrappers.lambdaUpdate(WfTask.class)
                .eq(WfTask::getId, taskId)
                .eq(WfTask::getStatus, WfTask.STATUS_PENDING)
                .set(WfTask::getStatus, WfTask.STATUS_APPROVED)
                .set(WfTask::getOpinion, opinion)
                .set(WfTask::getApproveTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.ofKey("erp.workflow.task-already-done", taskId);
        }
        // 或签：同节点其余待办置失效
        cancelSiblingPendingTasks(instance.getId(), task.getNodeId(), taskId);

        Map<String, Object> vars = readVars(instance);
        List<WfNode> nodes = orderedNodes(instance.getDefinitionId());
        WfNode next = nextActiveNode(nodes, task.getNodeOrder(), vars);
        if (next == null) {
            // 最后一个节点通过 → 实例整体通过
            finishInstance(instance, WfInstance.STATUS_AGREED);
            messageService.push(instance.getStartUserId(), WfMessage.TYPE_APPROVE,
                    "审批通过：" + instance.getBizNo(),
                    "您的单据[" + instance.getBizNo() + "]流程已全部审批通过",
                    "/workflow/todo");
            // 统一通知中心：审批结果事件（全部通过）→ 外部通道通知发起人
            eventPublisher.publishEvent(new NotifyEvents.ApprovalResultEvent(
                    instance.getId(), instance.getStartUserId(), instance.getBizType(), instance.getBizNo(),
                    true, task.getNodeName(), opinion, refMapper.selectUserNickname(CurrentUser.requireUserId())));
            return;
        }
        dispatchNode(instance, definition == null ? "" : definition.getFlowName(), next, vars);
        // 中间节点通过也通知发起人（知晓进展），金蝶同类"审批提醒"
        messageService.push(instance.getStartUserId(), WfMessage.TYPE_APPROVE,
                "节点通过：" + instance.getBizNo(),
                "您的单据[" + instance.getBizNo() + "]已通过节点[" + task.getNodeName() + "]，进入[" + next.getNodeName() + "]",
                "/workflow/todo");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long taskId, String opinion) {
        WfTask task = requireMyPendingTask(taskId);
        WfInstance instance = requireRunningInstance(task.getInstanceId());

        int rows = taskMapper.update(null, Wrappers.lambdaUpdate(WfTask.class)
                .eq(WfTask::getId, taskId)
                .eq(WfTask::getStatus, WfTask.STATUS_PENDING)
                .set(WfTask::getStatus, WfTask.STATUS_REJECTED)
                .set(WfTask::getOpinion, opinion)
                .set(WfTask::getApproveTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.ofKey("erp.workflow.task-already-done", taskId);
        }
        // 驳回即终结：全实例待办失效 + 实例 REJECTED
        cancelSiblingPendingTasks(instance.getId(), null, taskId);
        finishInstance(instance, WfInstance.STATUS_REJECTED);
        messageService.push(instance.getStartUserId(), WfMessage.TYPE_REJECT,
                "审批驳回：" + instance.getBizNo(),
                "您的单据[" + instance.getBizNo() + "]在节点[" + task.getNodeName() + "]被驳回"
                        + (opinion == null || opinion.isBlank() ? "" : "，意见：" + opinion),
                "/workflow/todo");
        // 统一通知中心：审批结果事件（驳回）→ 外部通道通知发起人
        eventPublisher.publishEvent(new NotifyEvents.ApprovalResultEvent(
                instance.getId(), instance.getStartUserId(), instance.getBizType(), instance.getBizNo(),
                false, task.getNodeName(), opinion, refMapper.selectUserNickname(CurrentUser.requireUserId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long instanceId) {
        Long userId = CurrentUser.requireUserId();
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw BusinessException.ofKey("erp.workflow.instance-not-exists", instanceId);
        }
        if (!instance.getStartUserId().equals(userId)) {
            throw BusinessException.ofKey("erp.workflow.only-initiator-can-revoke");
        }
        if (!WfInstance.STATUS_RUNNING.equals(instance.getStatus())) {
            throw BusinessException.ofKey("erp.workflow.instance-finished-no-revoke", instance.getStatus());
        }
        cancelSiblingPendingTasks(instanceId, null, null);
        finishInstance(instance, WfInstance.STATUS_CANCELLED);
    }

    @Override
    public PageResult<WfTaskVO> todoPage(WfTaskQuery query) {
        Long userId = CurrentUser.requireUserId();
        return taskPage(userId, false, query);
    }

    @Override
    public PageResult<WfTaskVO> donePage(WfTaskQuery query) {
        Long userId = CurrentUser.requireUserId();
        return taskPage(userId, true, query);
    }

    @Override
    public PageResult<WfInstanceVO> myInstancePage(WfInstanceQuery query) {
        Long userId = CurrentUser.requireUserId();
        var page = instanceMapper.selectInstancePage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                userId, query.getKeyword(), query.getBizType(), query.getStatus());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public com.erp.workflow.dto.vo.WfTaskDetailVO taskDetail(Long taskId) {
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.ofKey("erp.workflow.task-not-exists", taskId);
        }
        WfInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw BusinessException.ofKey("erp.workflow.instance-not-exists", task.getInstanceId());
        }
        WfDefinition definition = definitionMapper.selectById(instance.getDefinitionId());

        com.erp.workflow.dto.vo.WfTaskDetailVO detail = new com.erp.workflow.dto.vo.WfTaskDetailVO();
        detail.setId(task.getId());
        detail.setInstanceId(task.getInstanceId());
        detail.setNodeId(task.getNodeId());
        detail.setNodeOrder(task.getNodeOrder());
        detail.setNodeName(task.getNodeName());
        detail.setBizType(instance.getBizType());
        detail.setBizNo(instance.getBizNo());
        detail.setBizId(instance.getBizId());
        detail.setFlowName(definition == null ? null : definition.getFlowName());
        detail.setStartUserId(instance.getStartUserId());
        detail.setStartUserName(refMapper.selectUserNickname(instance.getStartUserId()));
        detail.setInstanceStatus(instance.getStatus());
        detail.setStatus(task.getStatus());
        detail.setOpinion(task.getOpinion());
        detail.setApproveTime(task.getApproveTime());
        detail.setCreateTime(task.getCreateTime());
        detail.setVars(instance.getVars());
        Map<String, Object> vars = readVars(instance);
        detail.setAmount(toAmount(vars));

        // 意见历史：实例下全部任务按节点序号升序（或签同节点多任务按处理时间）
        java.util.List<WfTask> instanceTasks = taskMapper.selectList(Wrappers.lambdaQuery(WfTask.class)
                        .eq(WfTask::getInstanceId, instance.getId()))
                .stream()
                .sorted(java.util.Comparator.comparing(WfTask::getNodeOrder)
                        .thenComparing(WfTask::getId))
                .toList();
        java.util.List<com.erp.workflow.dto.vo.WfTaskDetailVO.OpinionItem> history = new java.util.ArrayList<>();
        for (WfTask item : instanceTasks) {
            com.erp.workflow.dto.vo.WfTaskDetailVO.OpinionItem opinionItem =
                    new com.erp.workflow.dto.vo.WfTaskDetailVO.OpinionItem();
            opinionItem.setNodeName(item.getNodeName());
            opinionItem.setNodeOrder(item.getNodeOrder());
            opinionItem.setApproverId(item.getApproverId());
            opinionItem.setApproverName(refMapper.selectUserNickname(item.getApproverId()));
            opinionItem.setStatus(item.getStatus());
            opinionItem.setOpinion(item.getOpinion());
            opinionItem.setApproveTime(item.getApproveTime());
            history.add(opinionItem);
        }
        detail.setHistory(history);
        return detail;
    }

    /* ===================== 私有支撑 ===================== */

    /** 待办/已办分页共用查询 */
    private PageResult<WfTaskVO> taskPage(Long userId, boolean done, WfTaskQuery query) {
        var page = taskMapper.selectTaskPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                userId, done, query.getKeyword(), query.getBizType());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 取定义下节点并按序号升序 */
    private List<WfNode> orderedNodes(Long definitionId) {
        return nodeMapper.selectList(Wrappers.lambdaQuery(WfNode.class)
                        .eq(WfNode::getDefinitionId, definitionId))
                .stream()
                .sorted(Comparator.comparing(WfNode::getNodeOrder))
                .toList();
    }

    /**
     * 从 fromOrder 之后找下一个满足条件的节点（条件不满足即跳过）。
     *
     * @param nodes    定义节点（已按序升序）
     * @param fromOrder 起查序号（不含），传 0 表示从头找首个节点
     * @param vars     业务变量
     * @return 下一激活节点；无则 null（流程走完）
     */
    private WfNode nextActiveNode(List<WfNode> nodes, int fromOrder, Map<String, Object> vars) {
        for (WfNode node : nodes) {
            if (node.getNodeOrder() <= fromOrder) {
                continue;
            }
            if (ConditionEvaluator.matches(node.getConditionExpr(), vars)) {
                return node;
            }
            log.debug("节点[{}-{}]条件[{}]不满足，跳过", node.getId(), node.getNodeName(), node.getConditionExpr());
        }
        return null;
    }

    /**
     * 给节点派发任务并通知审批人。
     * USER → 指定用户一条；APPROVER → 角色下全部启用用户各一条（或签）。
     * 角色下无可用审批人则抛异常整体回滚（避免产生没有待办的"僵尸"运行实例）。
     */
    private void dispatchNode(WfInstance instance, String flowName, WfNode node, Map<String, Object> vars) {
        List<Long> approverIds;
        if (WfNode.TYPE_USER.equals(node.getApproverType())) {
            approverIds = List.of(node.getApproverId());
        } else {
            approverIds = refMapper.selectEnabledUserIdsByRole(node.getApproverId());
        }
        if (approverIds == null || approverIds.isEmpty()) {
            throw BusinessException.ofKey("erp.workflow.node-no-approver", node.getNodeName());
        }
        for (Long approverId : approverIds) {
            WfTask task = new WfTask();
            task.setInstanceId(instance.getId());
            task.setNodeId(node.getId());
            task.setNodeOrder(node.getNodeOrder());
            task.setNodeName(node.getNodeName());
            task.setApproverId(approverId);
            task.setStatus(WfTask.STATUS_PENDING);
            taskMapper.insert(task);
            messageService.push(approverId, WfMessage.TYPE_TODO,
                    "新待办：" + instance.getBizNo(),
                    "单据[" + instance.getBizNo() + "]待您审批（流程：" + flowName + "，节点：" + node.getNodeName() + "）",
                    "/workflow/todo");
            // 统一通知中心：任务生成事件 → 钉钉/企微/群机器人待办推送（事务提交后异步外发）
            eventPublisher.publishEvent(new NotifyEvents.TaskCreatedEvent(
                    task.getId(), approverId, instance.getBizType(), instance.getBizNo(),
                    toAmount(vars), instance.getStartUserId(),
                    refMapper.selectUserNickname(instance.getStartUserId()), flowName, node.getNodeName()));
        }
    }

    /** 流程变量 amount → BigDecimal（非数值/缺失返回 null） */
    private BigDecimal toAmount(Map<String, Object> vars) {
        Object value = vars == null ? null : vars.get("amount");
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return null;
    }

    /**
     * 置失效同实例的其余待办任务。
     *
     * @param nodeId 限定节点（null 表示全实例待办，驳回/撤回场景）
     * @param excludeTaskId 排除的任务（当前正处理的任务，null 不排除）
     */
    private void cancelSiblingPendingTasks(Long instanceId, Long nodeId, Long excludeTaskId) {
        var wrapper = Wrappers.lambdaUpdate(WfTask.class)
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getStatus, WfTask.STATUS_PENDING)
                .set(WfTask::getStatus, WfTask.STATUS_CANCELLED);
        if (nodeId != null) {
            wrapper.eq(WfTask::getNodeId, nodeId);
        }
        if (excludeTaskId != null) {
            wrapper.ne(WfTask::getId, excludeTaskId);
        }
        taskMapper.update(null, wrapper);
    }

    /** 实例收口（条件 UPDATE：仅 RUNNING 可终结，防并发重复收口） */
    private void finishInstance(WfInstance instance, String finalStatus) {
        int rows = instanceMapper.update(null, Wrappers.lambdaUpdate(WfInstance.class)
                .eq(WfInstance::getId, instance.getId())
                .eq(WfInstance::getStatus, WfInstance.STATUS_RUNNING)
                .set(WfInstance::getStatus, finalStatus)
                .set(WfInstance::getEndTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.ofKey("erp.workflow.instance-status-changed", instance.getId());
        }
    }

    /** 校验任务归属并返回（必须是当前登录人的待办） */
    private WfTask requireMyPendingTask(Long taskId) {
        Long userId = CurrentUser.requireUserId();
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.ofKey("erp.workflow.task-not-exists", taskId);
        }
        if (!task.getApproverId().equals(userId)) {
            throw BusinessException.ofKey("erp.workflow.task-not-owner");
        }
        if (!WfTask.STATUS_PENDING.equals(task.getStatus())) {
            throw BusinessException.ofKey("erp.workflow.task-done-status", task.getStatus());
        }
        return task;
    }

    /** 取运行中实例，非运行中直接抛错（结束的流程不允许再处理任务） */
    private WfInstance requireRunningInstance(Long instanceId) {
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw BusinessException.ofKey("erp.workflow.instance-not-exists", instanceId);
        }
        if (!WfInstance.STATUS_RUNNING.equals(instance.getStatus())) {
            throw BusinessException.ofKey("erp.workflow.instance-finished-no-task", instance.getStatus());
        }
        return instance;
    }

    /** vars 序列化（失败即发起失败，不允许带病流程上线） */
    private String writeVars(Map<String, Object> vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.workflow.variable-serialize-failed", e.getMessage());
        }
    }

    /** vars 反序列化（坏数据兜底为空 Map：最多让条件不满足跳过，不让流程卡死） */
    private Map<String, Object> readVars(WfInstance instance) {
        if (instance.getVars() == null || instance.getVars().isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(instance.getVars(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("实例[{}]流程变量[{}]反序列化失败，按空变量处理: {}", instance.getId(), instance.getVars(), e.getMessage());
            return new HashMap<>();
        }
    }
}
