package com.erp.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购合同价新增/修改表单。
 */
@Data
public class PriceContractForm {

    /** 供应商 ID */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 合同单价（不含税） */
    @NotNull(message = "合同价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "合同价必须大于0")
    private BigDecimal contractPrice;

    /** 生效日期起（含） */
    @NotNull(message = "生效起不能为空")
    private LocalDate effectiveFrom;

    /** 生效日期止（含） */
    @NotNull(message = "生效止不能为空")
    private LocalDate effectiveTo;

    /** 状态：1-启用 0-停用（空默认 1） */
    private Integer status;

    /** 备注（合同号等） */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
