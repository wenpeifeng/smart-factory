package com.erp.workflow.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 发起流程表单（POST /workflow/instances）。
 * <p>
 * vars 为业务变量（如 amount=订单金额），仅支持数值/字符串/布尔，
 * 序列化为 JSON 存实例，供节点条件求值。
 */
@Data
public class StartFlowForm {

    /** 单据类型（dict:wf_biz_type） */
    @NotBlank(message = "单据类型不能为空")
    private String bizType;

    /** 业务单据 ID */
    @NotNull(message = "单据ID不能为空")
    private Long bizId;

    /** 业务单号（展示用） */
    @NotBlank(message = "单号不能为空")
    @Size(max = 40, message = "单号最长 40 字符")
    private String bizNo;

    /** 业务变量（如 {"amount": 8000}） */
    private Map<String, Object> vars;
}
