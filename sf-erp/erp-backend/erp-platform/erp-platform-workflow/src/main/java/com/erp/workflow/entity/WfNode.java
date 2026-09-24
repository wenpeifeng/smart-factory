package com.erp.workflow.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批流程节点实体（wf_node）。
 * <p>
 * 节点按 {@code nodeOrder} 从小到大串行流转；{@code conditionExpr} 非空时
 * 以发起时的业务变量（vars）求值，不满足则跳过本节点（轻量条件路由）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfNode extends BaseEntity {

    /** 审批人类型：按角色（或签 —— 角色下任一成员处理即过） */
    public static final String TYPE_ROLE = "APPROVER";
    /** 审批人类型：指定用户 */
    public static final String TYPE_USER = "USER";

    /** 流程定义 ID（wf_definition.id） */
    private Long definitionId;

    /** 节点序号（从 1 开始，按序串行流转） */
    private Integer nodeOrder;

    /** 节点名称（如 部门经理审批） */
    private String nodeName;

    /** 审批人类型：APPROVER角色 USER指定用户 */
    private String approverType;

    /** 审批人 ID：APPROVER 时为角色 ID，USER 时为用户 ID */
    private Long approverId;

    /** 条件表达式（可选，如 amount>10000；变量取自发起时的 vars） */
    private String conditionExpr;
}
