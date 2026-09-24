package com.erp.workflow.dto.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用/停用表单（流程定义状态切换）。
 */
@Data
public class StatusForm {

    /** 目标状态：1启用 0停用 */
    @NotNull(message = "状态值不能为空")
    private Integer status;
}
