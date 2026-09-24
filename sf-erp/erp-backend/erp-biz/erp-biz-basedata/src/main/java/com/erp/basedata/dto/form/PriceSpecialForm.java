package com.erp.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 特价申请新增表单（提交后自动尝试挂 workflow，无启用流程则管理员简单审批）。
 */
@Data
public class PriceSpecialForm {

    /** 客户 ID */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 特价（不含税） */
    @NotNull(message = "特价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "特价必须大于0")
    private BigDecimal specialPrice;

    /** 申请理由 */
    @Size(max = 200, message = "申请理由长度不能超过200")
    private String applyReason;

    /** 生效起（含，空=不限） */
    private LocalDate validFrom;

    /** 生效止（含，空=不限） */
    private LocalDate validTo;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
