package com.erp.cost.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工单制造费用分摊录入表单（POST /cost/work-orders/{id}/overhead）。
 * <p>
 * 整体覆盖式录入（每次提交覆盖该工单的费/工时快照），料/工不入库、实时归集。
 */
@Data
public class OverheadForm {

    /** 制造费用分摊金额（≥0） */
    @NotNull(message = "制造费用金额不能为空")
    @DecimalMin(value = "0", message = "制造费用金额不能为负")
    private BigDecimal overheadAmount;

    /** 工时（小时，≥0，费用分摊依据） */
    @NotNull(message = "工时不能为空")
    @DecimalMin(value = "0", message = "工时不能为负")
    private BigDecimal laborHours;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
