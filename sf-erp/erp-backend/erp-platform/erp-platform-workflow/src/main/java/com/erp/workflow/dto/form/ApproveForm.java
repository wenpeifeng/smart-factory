package com.erp.workflow.dto.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批表单（通过/驳回共用：意见可选）。
 */
@Data
public class ApproveForm {

    /** 审批意见（可选） */
    @Size(max = 500, message = "审批意见最长 500 字符")
    private String opinion;
}
