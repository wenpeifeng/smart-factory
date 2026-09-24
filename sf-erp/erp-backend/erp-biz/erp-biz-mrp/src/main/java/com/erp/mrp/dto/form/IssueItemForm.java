package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 领料单子件提交项（可省略，省略时按 BOM 全量带出）。
 */
@Data
public class IssueItemForm implements Serializable {

    /** 子件物料ID */
    @NotNull(message = "子件物料不能为空")
    private Long materialId;

    /** 实领数量 */
    @NotNull(message = "实领数量不能为空")
    @DecimalMin(value = "0", message = "实领数量不能为负")
    private BigDecimal actualQty;
}
