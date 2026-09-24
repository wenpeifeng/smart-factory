package com.mes.workflow.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批任务实体（wf_task）。
 * <p>
 * 每个节点到达时按审批人生成任务：USER 节点一条；角色（APPROVER）节点
 * 对角色下每个启用用户各生成一条（或签 —— 任一成员通过/驳回即推进，
 * 同节点其余 PENDING 任务置 CANCELLED）。
 * nodeOrder/nodeName 为节点快照，防止定义后续修改影响历史任务展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfTask extends BaseEntity {

    /** 状态：待审批 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：已失效（同节点他人先处理或实例撤回） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 流程实例 ID（wf_instance.id） */
    private Long instanceId;

    /** 节点 ID（wf_node.id，定义侧引用） */
    private Long nodeId;

    /** 节点序号（冗余快照） */
    private Integer nodeOrder;

    /** 节点名称（冗余快照） */
    private String nodeName;

    /** 审批人用户 ID（sys_user.id） */
    private Long approverId;

    /** 状态：PENDING/APPROVED/REJECTED/CANCELLED */
    private String status;

    /** 审批意见 */
    private String opinion;

    /** 审批时间 */
    private java.time.LocalDateTime approveTime;
}
