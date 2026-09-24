package com.erp.sales.dto.form;

import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * 报价单下推销售订单表单。
 * <p>
 * 仓库可不选：为空时取系统第一个在用仓库兜底；订单日期默认当天。
 */
@Data
public class SalQuotationToOrderForm {

    /** 发货仓库 ID（可空 = 取第一个在用仓库） */
    private Long warehouseId;

    /** 订单日期（可空 = 当天，不允许晚于今天） */
    @PastOrPresent(message = "订单日期不能晚于当天")
    private LocalDate orderDate;
}
