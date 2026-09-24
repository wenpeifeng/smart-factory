package com.mes.workflow.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点 VO（定义详情子表）。
 */
@Data
public class WfNodeVO {

    /** 节点 ID */
    private Long id;

    /** 节点序号 */
    private Integer nodeOrder;

    /** 节点名称 */
    private String nodeName;

    /** 审批人类型：APPROVER角色 USER指定用户 */
    private String approverType;

    /** 审批人 ID（角色 ID 或用户 ID） */
    private Long approverId;

    /** 审批人展示名（角色名或用户姓名，冗余便于展示） */
    private String approverName;

    /** 条件表达式（如 amount>10000） */
    private String conditionExpr;

    /** 创建时间 */
    private LocalDateTime createTime;
}
