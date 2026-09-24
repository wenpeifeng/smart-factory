package com.erp.workflow.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 流程定义新增/修改表单（含节点子表整体提交）。
 * <p>
 * 修改为整体覆盖节点（删旧插新），与采购单明细的处理口径一致；
 * flowCode 全局唯一；bizType 必须在白名单内（WfConstants.BIZ_TYPES）。
 */
@Data
public class WfDefinitionForm {

    /** 流程编码（唯一，字母开头，字母/数字/下划线/中划线） */
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 40, message = "流程编码最长 40 字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$", message = "流程编码须字母开头，仅含字母/数字/下划线/中划线")
    private String flowCode;

    /** 流程名称 */
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 60, message = "流程名称最长 60 字符")
    private String flowName;

    /** 适用单据类型（dict:wf_biz_type） */
    @NotBlank(message = "适用单据类型不能为空")
    private String bizType;

    /** 状态：1启用 0停用 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字符")
    private String remark;

    /** 节点列表（整体覆盖，至少一个） */
    @NotEmpty(message = "流程节点不能为空")
    @Valid
    private List<NodeForm> nodes;

    /**
     * 流程节点表单。
     */
    @Data
    public static class NodeForm {

        /** 节点序号（从 1 开始） */
        @NotNull(message = "节点序号不能为空")
        @Positive(message = "节点序号必须为正整数")
        private Integer nodeOrder;

        /** 节点名称 */
        @NotBlank(message = "节点名称不能为空")
        @Size(max = 60, message = "节点名称最长 60 字符")
        private String nodeName;

        /** 审批人类型：APPROVER角色 USER指定用户 */
        @NotBlank(message = "审批人类型不能为空")
        @Pattern(regexp = "APPROVER|USER", message = "审批人类型仅支持 APPROVER(角色)/USER(指定用户)")
        private String approverType;

        /** 审批人 ID：APPROVER=角色ID，USER=用户ID */
        @NotNull(message = "审批人ID不能为空")
        @Positive(message = "审批人ID必须为正数")
        private Long approverId;

        /** 条件表达式（可选，如 amount>10000） */
        @Size(max = 200, message = "条件表达式最长 200 字符")
        private String conditionExpr;
    }
}
