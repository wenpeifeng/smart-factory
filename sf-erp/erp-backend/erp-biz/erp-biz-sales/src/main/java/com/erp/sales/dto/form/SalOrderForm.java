package com.erp.sales.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售订单新增/修改提交表单。
 * <p>
 * Entity 不直接作为写接口入参（防 mass assignment），统一走本 Form。
 */
@Data
public class SalOrderForm {

    /** 客户 ID */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 发货仓库 ID */
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    /** 订单日期（不允许晚于今天） */
    @NotNull(message = "订单日期不能为空")
    @PastOrPresent(message = "订单日期不能晚于当天")
    private LocalDate orderDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 明细行（至少一行） */
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<SalOrderDetailForm> details;
}
