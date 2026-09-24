package com.mes.notify.core;

/**
 * 审批回调路由接口 —— 由 mes-workflow 模块实现（依赖方向：workflow → notify）。
 * <p>
 * 钉钉/企微开放平台回调（手机端卡片上的通过/驳回按钮）经 notify 的
 * /notify/callback/* 验签解密后，通过本接口路由到审批引擎；
 * notify 不反向依赖 workflow，回调处理器未装配时（workflow 模块未启用）
 * 回调接口返回"未接审批回调处理器"提示。
 */
public interface ApprovalCallbackHandler {

    /**
     * 处理一条审批动作回调。
     *
     * @param source  回调来源（dingtalk/wecom，定位与日志用）
     * @param taskId  MES 审批任务 ID（wf_task.id，推送链接 /m/approve/{taskId} 的同一 ID）
     * @param approve true=通过 false=驳回
     * @param opinion 审批意见（可空；驳回建议必填）
     * @return 处理结果描述（成功/失败原因，回调响应体回传给开放平台）
     */
    String handleApproval(String source, Long taskId, boolean approve, String opinion);
}
