package com.mes.workflow.dto.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 引擎验证演示表单（POST /workflow/demo/start）。
 * <p>
 * 仅用于验证审批引擎闭环（一期单据模块集成前），不落业务表：
 * bizId 用时间戳模拟，bizNo 用 DEMO 前缀，vars 携带 amount 供条件路由。
 */
@Data
public class DemoStartForm {

    /** 单据类型（dict:wf_biz_type） */
    @NotBlank(message = "单据类型不能为空")
    private String bizType;

    /** 模拟单据金额（条件路由变量 amount） */
    private BigDecimal amount;
}
