package com.erp.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售批量价新增/修改表单（物料 × 起订量阶梯）。
 */
@Data
public class PriceQtyForm {

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 起订量（含） */
    @NotNull(message = "起订量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "起订量必须大于0")
    private BigDecimal minQty;

    /** 批量单价（不含税） */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "单价必须大于0")
    private BigDecimal price;

    /** 状态：1-启用 0-停用（空默认 1） */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
