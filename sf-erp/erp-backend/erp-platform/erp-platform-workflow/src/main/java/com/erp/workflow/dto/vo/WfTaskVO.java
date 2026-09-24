package com.erp.workflow.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务 VO（我的待办/已办，带实例与单据冗余信息）。
 */
@Data
public class WfTaskVO {

    /** 任务 ID */
    private Long id;

    /** 实例 ID */
    private Long instanceId;

    /** 节点 ID */
    private Long nodeId;

    /** 节点序号 */
    private Integer nodeOrder;

    /** 节点名称 */
    private String nodeName;

    /** 单据类型 */
    private String bizType;

    /** 业务单号 */
    private String bizNo;

    /** 业务单据 ID */
    private Long bizId;

    /** 流程名称 */
    private String flowName;

    /** 发起人 ID */
    private Long startUserId;

    /** 发起人姓名 */
    private String startUserName;

    /** 实例状态：RUNNING/AGREED/REJECTED/CANCELLED */
    private String instanceStatus;

    /** 任务状态：PENDING/APPROVED/REJECTED/CANCELLED */
    private String status;

    /** 审批意见 */
    private String opinion;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 任务到达时间 */
    private LocalDateTime createTime;
}
