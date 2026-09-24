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
 * 销售出库单新增提交表单（从销售订单选单生成）。
 * <p>
 * 明细行通过 orderDetailId 引用订单行，出库数量不得超过该行剩余数量。
 */
@Data
public class SalOutstockForm {

    /** 来源销售订单 ID */
    @NotNull(message = "销售订单不能为空")
    private Long orderId;

    /** 出库日期 */
    @NotNull(message = "出库日期不能为空")
    @PastOrPresent(message = "出库日期不能晚于当天")
    private LocalDate outDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 明细行（至少一行） */
    @NotEmpty(message = "出库明细不能为空")
    @Valid
    private List<SalOutstockDetailForm> details;
}
