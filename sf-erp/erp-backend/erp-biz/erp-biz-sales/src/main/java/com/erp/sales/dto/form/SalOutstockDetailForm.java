package com.erp.sales.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售出库单明细行提交表单。
 */
@Data
public class SalOutstockDetailForm {

    /** 来源订单行 ID（sal_order_detail.id） */
    @NotNull(message = "订单行不能为空")
    private Long orderDetailId;

    /** 出库数量（> 0，且不得超过订单行剩余数量，服务端校验） */
    @NotNull(message = "出库数量不能为空")
    @DecimalMin(value = "0.0001", message = "出库数量必须大于 0")
    @Digits(integer = 14, fraction = 4, message = "出库数量最多 4 位小数")
    private BigDecimal qty;
}
