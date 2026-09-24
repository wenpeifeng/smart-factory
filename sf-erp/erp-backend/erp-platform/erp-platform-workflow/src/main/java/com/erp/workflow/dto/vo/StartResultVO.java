package com.erp.workflow.dto.vo;

import lombok.Data;

/**
 * 发起流程结果 VO（WorkflowService#startFlow 返回）。
 * <p>
 * status 三态：
 * <ul>
 *   <li>SKIPPED —— 无匹配的启用流程，单据视为"免审直接过"（isApproved=true）；</li>
 *   <li>RUNNING —— 已生成实例并落在第一个满足条件的节点（单据应置"审批中"）；</li>
 *   <li>AGREED  —— 流程所有节点条件均不满足（全跳过），实例即时通过（isApproved=true）。</li>
 * </ul>
 */
@Data
public class StartResultVO {

    /** 发起结果：SKIPPED无流程 RUNNING审批中 AGREED即时通过 */
    private String status;

    /** 实例 ID（SKIPPED 时为 null） */
    private Long instanceId;

    /** 说明文案（供调用方直接提示/打日志） */
    private String message;

    /** 静态工厂：无匹配启用流程 */
    public static StartResultVO skipped() {
        StartResultVO vo = new StartResultVO();
        vo.setStatus("SKIPPED");
        vo.setMessage("无匹配的启用流程，单据免审直接过");
        return vo;
    }

    /** 静态工厂：已发起（审批中） */
    public static StartResultVO running(Long instanceId) {
        StartResultVO vo = new StartResultVO();
        vo.setStatus("RUNNING");
        vo.setInstanceId(instanceId);
        vo.setMessage("流程已发起，进入审批中");
        return vo;
    }

    /** 静态工厂：所有节点条件不满足，即时通过 */
    public static StartResultVO agreed(Long instanceId) {
        StartResultVO vo = new StartResultVO();
        vo.setStatus("AGREED");
        vo.setInstanceId(instanceId);
        vo.setMessage("流程所有节点条件均不满足，即时通过");
        return vo;
    }
}
