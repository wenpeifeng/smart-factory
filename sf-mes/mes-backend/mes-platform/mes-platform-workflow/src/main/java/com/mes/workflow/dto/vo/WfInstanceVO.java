package com.mes.workflow.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程实例 VO（我发起的流程列表）。
 */
@Data
public class WfInstanceVO {

    /** 实例 ID */
    private Long id;

    /** 流程定义 ID */
    private Long definitionId;

    /** 流程名称 */
    private String flowName;

    /** 单据类型 */
    private String bizType;

    /** 业务单据 ID */
    private Long bizId;

    /** 业务单号 */
    private String bizNo;

    /** 状态：RUNNING/AGREED/REJECTED/CANCELLED */
    private String status;

    /** 当前待办节点名（多个逗号分隔，运行中才有值） */
    private String currentNode;

    /** 发起人 ID */
    private Long startUserId;

    /** 发起人姓名 */
    private String startUserName;

    /** 发起时间 */
    private LocalDateTime createTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
