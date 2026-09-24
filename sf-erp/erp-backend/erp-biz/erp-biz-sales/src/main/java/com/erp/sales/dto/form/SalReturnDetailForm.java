package com.erp.sales.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售退货单明细行提交表单。
 */
@Data
public class SalReturnDetailForm {

    /** 来源出库行 ID（sal_outstock_detail.id） */
    @NotNull(message = "出库行不能为空")
    private Long outDetailId;

    /** 退货入库批次号（批次物料回补必填——审核时 StockService 强校验；非批次物料忽略） */
    @Size(max = 50, message = "批次号最长 50 字")
    private String batchNo;

    /** 退货数量（> 0，且不得超过出库行可退数量，服务端校验） */
    @NotNull(message = "退货数量不能为空")
    @DecimalMin(value = "0.0001", message = "退货数量必须大于 0")
    @Digits(integer = 14, fraction = 4, message = "退货数量最多 4 位小数")
    private BigDecimal qty;
}
