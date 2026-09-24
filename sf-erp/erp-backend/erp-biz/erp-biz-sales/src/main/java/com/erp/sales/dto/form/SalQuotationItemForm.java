package com.erp.sales.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售报价单明细行提交表单。
 */
@Data
public class SalQuotationItemForm {

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 报价数量（> 0，最多 4 位小数） */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于 0")
    @Digits(integer = 14, fraction = 4, message = "数量最多 4 位小数")
    private BigDecimal qty;

    /** 报价单价（≥ 0，最多 2 位小数） */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0", message = "单价不能为负")
    @Digits(integer = 16, fraction = 2, message = "单价最多 2 位小数")
    private BigDecimal price;

    /** 行备注（可选） */
    @Size(max = 200, message = "行备注最长 200 字")
    private String remark;
}
