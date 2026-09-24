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
 * 销售退货单新增提交表单（引用销售出库单退货）。
 */
@Data
public class SalReturnForm {

    /** 来源销售出库单 ID */
    @NotNull(message = "销售出库单不能为空")
    private Long outId;

    /** 退货日期 */
    @NotNull(message = "退货日期不能为空")
    @PastOrPresent(message = "退货日期不能晚于当天")
    private LocalDate returnDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 明细行（至少一行） */
    @NotEmpty(message = "退货明细不能为空")
    @Valid
    private List<SalReturnDetailForm> details;
}
