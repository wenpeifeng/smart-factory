package com.erp.purchase.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 暂估手动冲回表单。
 * <p>
 * 口径：actualAmount=发票实际金额（价税合计，应付口径）；
 * 本次冲回暂估金额 = min(actualAmount, 未冲回余额)，未冲完置"部分冲回"。
 */
@Data
public class EstimationReverseForm {

    /** 冲回所用发票号 */
    @NotBlank(message = "发票号不能为空")
    @Size(max = 20, message = "发票号长度不能超过 20")
    private String invoiceNo;

    /** 发票实际金额（价税合计，>0） */
    @NotNull(message = "发票实际金额不能为空")
    @DecimalMin(value = "0.01", message = "发票实际金额必须大于 0")
    private BigDecimal actualAmount;
}
