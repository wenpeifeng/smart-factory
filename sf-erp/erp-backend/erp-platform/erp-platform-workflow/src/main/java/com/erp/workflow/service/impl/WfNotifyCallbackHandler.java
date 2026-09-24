package com.erp.workflow.service.impl;

import com.erp.notify.core.ApprovalCallbackHandler;
import com.erp.workflow.entity.WfTask;
import com.erp.workflow.mapper.WfTaskMapper;
import com.erp.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 审批回调处理器 —— 钉钉/企微开放平台回调路由到审批引擎（notify 模块 SPI 的 workflow 侧实现）。
 * <p>
 * approve/reject 要求"当前登录人=任务审批人"（CurrentUser 读 SecurityContext），
 * 回调请求无 JWT，这里临时以任务审批人身份建立认证上下文（用后即清，仅本线程可见），
 * 复用与 H5 完全相同的审批入口（条件 UPDATE 防并发、或签失效、消息/事件链路全部一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfNotifyCallbackHandler implements ApprovalCallbackHandler {

    private final WfTaskMapper taskMapper;
    private final WorkflowService workflowService;

    @Override
    public String handleApproval(String source, Long taskId, boolean approve, String opinion) {
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return "任务[" + taskId + "]不存在";
        }
        if (!WfTask.STATUS_PENDING.equals(task.getStatus())) {
            return "任务[" + taskId + "]已处理（" + task.getStatus() + "）";
        }
        // 以任务审批人身份执行（回调无登录态；用后即清）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(task.getApproverId(), null, java.util.List.of()));
        try {
            if (approve) {
                workflowService.approve(taskId, opinion);
            } else {
                workflowService.reject(taskId, opinion);
            }
            log.info("[工作流] {} 回调审批完成：taskId={} action={}", source, taskId, approve ? "通过" : "驳回");
            return "ok";
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
