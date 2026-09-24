package com.erp.qc.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 不合格品处置单新增/修改表单。
 */
@Data
public class QcRejectForm {

    /** 来源检验单 ID（须为已检验且不合格数>0） */
    @NotNull(message = "来源检验单不能为空")
    private Long inspectId;

    /** 处置数量（>0，累计 ≤ 检验单不合格数） */
    @NotNull(message = "处置数量不能为空")
    @DecimalMin(value = "0.0001", message = "处置数量必须大于 0")
    private BigDecimal qty;

    /** 处置决定：1退货 2返工 3让步接收 4报废 */
    @NotNull(message = "处置决定不能为空")
    @Min(value = 1, message = "处置决定取值不合法")
    @Max(value = 4, message = "处置决定取值不合法")
    private Integer decision;

    /** 处置说明 */
    @Size(max = 500, message = "处置说明最长 500 字")
    private String disposeRemark;

    /** 审批人 */
    @Size(max = 50, message = "审批人最长 50 字")
    private String approver;
}
