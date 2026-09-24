package com.mes.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用/停用表单（用户等资源的通用状态切换）。
 */
@Data
public class StatusForm {

    /** 目标状态：1-启用 0-停用 */
    @NotNull(message = "状态值不能为空")
    private Integer status;
}
