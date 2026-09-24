package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 子件提交项 —— 用量必须为正、损耗率 [0,100]。
 */
@Data
public class BomItemForm implements Serializable {

    /** 子件物料ID */
    @NotNull(message = "子件物料不能为空")
    private Long materialId;

    /** 单位用量（单件成品耗用） */
    @NotNull(message = "用量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "用量必须大于0")
    private BigDecimal qty;

    /** 损耗率% */
    @DecimalMin(value = "0", message = "损耗率不能小于0")
    @DecimalMax(value = "100", message = "损耗率不能超过100")
    private BigDecimal lossRate = BigDecimal.ZERO;

    /** 备注 */
    private String remark;
}
