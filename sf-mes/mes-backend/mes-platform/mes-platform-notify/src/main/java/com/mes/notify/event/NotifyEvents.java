package com.mes.notify.event;

import java.math.BigDecimal;

/**
 * 通知事件定义 —— 统一通知中心的事件契约。
 * <p>
 * 由 mes-workflow 在三处业务点 publish（Spring 应用事件），
 * mes-platform-notify 监听后路由到启用的通知通道：
 * <ul>
 *   <li>{@link TaskCreatedEvent}：任务生成点（WorkflowServiceImpl.dispatchNode）→ 待办推送；</li>
 *   <li>{@link ApprovalResultEvent}：实例终结点（approve 最终通过 / reject 驳回）→ 审批结果推送；</li>
 *   <li>{@link WarnEvent}：预警扫描点（WfWarnServiceImpl 三类扫描）→ 预警群通知。</li>
 * </ul>
 * 事件类放在 notify 模块、workflow 依赖 notify（仅事件契约，反向不依赖），
 * 回调审批路由经 {@code ApprovalCallbackHandler} 接口反转，避免循环依赖。
 */
public final class NotifyEvents {

    private NotifyEvents() {
    }

    /** 审批待办事件（一个审批人一条；或签角色节点会对每个成员各发一条） */
    public record TaskCreatedEvent(
            Long taskId,
            Long assigneeId,
            String bizType,
            String bizNo,
            BigDecimal amount,
            Long startUserId,
            String startUserName,
            String flowName,
            String nodeName) {
    }

    /** 审批结果事件（实例终态：全部通过 / 驳回，接收人=发起人） */
    public record ApprovalResultEvent(
            Long instanceId,
            Long startUserId,
            String bizType,
            String bizNo,
            boolean approved,
            String nodeName,
            String opinion,
            String operatorName) {
    }

    /** 预警事件（库存安全线/客户信用/生产超期，群机器人推送） */
    public record WarnEvent(
            String warnType,
            String title,
            String content,
            String bizPath,
            Long receiverUserId) {
    }
}
