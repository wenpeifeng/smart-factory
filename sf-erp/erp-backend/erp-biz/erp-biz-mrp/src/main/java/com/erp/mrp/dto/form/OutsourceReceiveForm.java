package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 委外完工入库表单。
 */
@Data
public class OutsourceReceiveForm implements Serializable {

    /** 本次入库良品数量 */
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "入库数量必须大于0")
    private BigDecimal goodQty;

    /** 入库仓库（可空=使用委外单默认入库仓库） */
    private Long warehouseId;

    /** 入库批次号（批次物料成品必填——由 StockService 强校验；非批次物料忽略） */
    @Size(max = 50, message = "批次号最长 50 字")
    private String batchNo;
}
