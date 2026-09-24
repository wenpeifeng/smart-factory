package com.erp.equipment.dto.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保养执行表单（POST /equipment/plans/{id}/execute 的可选请求体）。
 */
@Data
public class PlanExecuteForm {

    /** 执行人（文本，留空记"系统"） */
    @Size(max = 50, message = "执行人长度不能超过 50")
    private String executor;

    /** 保养内容 */
    @Size(max = 500, message = "保养内容长度不能超过 500")
    private String content;
}
