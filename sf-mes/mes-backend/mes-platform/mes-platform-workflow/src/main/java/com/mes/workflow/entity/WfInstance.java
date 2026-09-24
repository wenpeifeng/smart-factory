package com.mes.workflow.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批流程实例实体（wf_instance）。
 * <p>
 * 一张业务单据（bizType + bizId）同时最多一个运行中实例（幂等保证，见
 * WorkflowServiceImpl#startFlow）；实例冗余 vars(JSON) 保存发起时的业务变量，
 * 供后续节点条件求值 —— 单据后续修改不影响在途流程的走向判断。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfInstance extends BaseEntity {

    /** 状态：审批中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 状态：已同意（全部节点通过） */
    public static final String STATUS_AGREED = "AGREED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：已撤回（发起人主动撤销） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 流程定义 ID（wf_definition.id） */
    private Long definitionId;

    /** 单据类型（冗余，便于按单据查询） */
    private String bizType;

    /** 业务单据 ID（如 pur_order.id） */
    private Long bizId;

    /** 业务单号（如 PO202609050001，展示用） */
    private String bizNo;

    /** 状态：RUNNING/AGREED/REJECTED/CANCELLED */
    private String status;

    /** 发起人用户 ID（sys_user.id） */
    private Long startUserId;

    /** 发起时的业务变量 JSON（如 {"amount":8000}） */
    private String vars;

    /** 结束时间（同意/驳回/撤回时间，运行中为空） */
    private java.time.LocalDateTime endTime;
}
