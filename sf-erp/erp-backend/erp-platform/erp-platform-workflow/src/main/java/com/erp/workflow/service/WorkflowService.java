package com.erp.workflow.service;

import com.erp.common.result.PageResult;
import com.erp.workflow.dto.query.WfInstanceQuery;
import com.erp.workflow.dto.query.WfTaskQuery;
import com.erp.workflow.dto.vo.StartResultVO;
import com.erp.workflow.dto.vo.WfInstanceVO;
import com.erp.workflow.dto.vo.WfTaskVO;

import java.util.Map;

/**
 * WorkflowService —— 审批引擎对外核心服务（跨模块集成唯一入口）。
 * <p>
 * 一期/二期业务单据模块（采购/销售/库存/生产/财务）接入审批流的唯一约定：
 * <pre>
 *   // 单据"审核"动作改为三段式：
 *   StartResultVO r = workflowService.startFlow(bizType, bizId, bizNo, Map.of("amount", total));
 *   if ("SKIPPED".equals(r.getStatus()) || "AGREED".equals(r.getStatus())) {
 *       // 无流程/条件全不满足 → 直接执行原审核逻辑（单据→已审核）
 *   } else {
 *       // RUNNING → 单据置"审批中"，等审批完成（isApproved==true）后由回调/轮询执行原审核逻辑
 *   }
 *   // 任意时点查询：workflowService.isApproved(bizType, bizId)（无流程=true）
 * </pre>
 * 引擎语义：
 * <ul>
 *   <li>节点按序号串行；条件表达式（如 amount&gt;10000）不满足则跳过该节点；</li>
 *   <li>角色节点为"或签"：角色下每个启用用户各生成一条待办，任一成员处理即推进；</li>
 *   <li>同一单据重复发起幂等：已有运行中实例直接返回该实例；</li>
 *   <li>通过/驳回/新待办自动写消息中心（wf_message）。</li>
 * </ul>
 */
public interface WorkflowService {

    /**
     * 发起审批流程。
     *
     * @param bizType 单据类型（dict:wf_biz_type：pur_order/sal_order/pur_instock/sal_outstock/mrp_work_order/fin_payment/fin_receipt）
     * @param bizId   业务单据 ID（如 pur_order.id）
     * @param bizNo   业务单号（展示用，如 PO202609050001）
     * @param vars    业务变量（如 {"amount": 8000}），供节点条件求值；可为 null
     * @return 发起结果：SKIPPED（无匹配启用流程，单据直接过）/ RUNNING（审批中）/ AGREED（节点条件全不满足，即时通过）
     * @throws com.erp.common.exception.BusinessException 单据类型不在白名单、角色节点无可用审批人等
     */
    StartResultVO startFlow(String bizType, Long bizId, String bizNo, Map<String, Object> vars);

    /**
     * 查询单据是否审批通过。
     * <p>无流程实例（未接入/无匹配流程）视为通过=true；仅最新实例状态 AGREED 为通过。
     *
     * @param bizType 单据类型
     * @param bizId   业务单据 ID
     * @return true=通过（含无流程）；false=审批中/已驳回/已撤回
     */
    boolean isApproved(String bizType, Long bizId);

    /**
     * 审批通过当前任务（仅任务审批人本人可操作）。
     * <p>通过后推进：同节点其余待办置 CANCELLED（或签）；有下一满足条件的节点则生成新待办，
     * 否则实例置 AGREED。通过即给发起人发 APPROVE 消息，新待办给下一审批人发 TODO 消息。
     *
     * @param taskId  任务 ID
     * @param opinion 审批意见（可空）
     * @throws com.erp.common.exception.BusinessException 任务不存在/无权处理/已被处理/实例非运行中
     */
    void approve(Long taskId, String opinion);

    /**
     * 驳回当前任务（仅任务审批人本人可操作）。
     * <p>实例整体置 REJECTED（终结），其余待办全部置 CANCELLED，并给发起人发 REJECT 消息。
     *
     * @param taskId  任务 ID
     * @param opinion 审批意见（可空）
     * @throws com.erp.common.exception.BusinessException 任务不存在/无权处理/已被处理/实例非运行中
     */
    void reject(Long taskId, String opinion);

    /**
     * 撤回流程实例（仅发起人本人，仅运行中可撤回）。
     * <p>实例置 CANCELLED，全部待办置 CANCELLED。
     *
     * @param instanceId 实例 ID
     * @throws com.erp.common.exception.BusinessException 非发起人/实例非运行中
     */
    void cancel(Long instanceId);

    /**
     * 我的待办任务分页。
     *
     * @param query 查询条件（关键词/单据类型）
     * @return 待办任务分页
     */
    PageResult<WfTaskVO> todoPage(WfTaskQuery query);

    /**
     * 我的已办任务分页（通过/驳回/失效）。
     *
     * @param query 查询条件（关键词/单据类型）
     * @return 已办任务分页
     */
    PageResult<WfTaskVO> donePage(WfTaskQuery query);

    /**
     * 我发起的流程实例分页。
     *
     * @param query 查询条件（关键词/单据类型/状态）
     * @return 实例分页
     */
    PageResult<WfInstanceVO> myInstancePage(WfInstanceQuery query);

    /**
     * 任务详情（移动端审批页 /m/approve/{taskId}）：任务冗余信息 + 单据金额
     * （实例 vars.amount）+ 实例已处理节点的意见历史。
     *
     * @param taskId 任务 ID
     * @return 任务详情（含意见历史）
     * @throws com.erp.common.exception.BusinessException 任务不存在
     */
    com.erp.workflow.dto.vo.WfTaskDetailVO taskDetail(Long taskId);
}
