package com.erp.sales.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发货通知单明细行提交表单。
 * <p>
 * 从订单生成时填 orderDetailId（数量守卫订单行余量）；
 * 直发时填 materialId。两者服务端校验二选一。
 */
@Data
public class SalDeliveryNoticeItemForm {

    /** 来源订单明细 ID（从订单生成时必填） */
    private Long orderDetailId;

    /** 物料 ID（直发时必填） */
    private Long materialId;

    /** 通知数量（> 0，最多 4 位小数） */
    @NotNull(message = "通知数量不能为空")
    @DecimalMin(value = "0.0001", message = "通知数量必须大于 0")
    @Digits(integer = 14, fraction = 4, message = "通知数量最多 4 位小数")
    private BigDecimal qty;

    /** 行备注（可选） */
    @Size(max = 200, message = "行备注最长 200 字")
    private String remark;
}
