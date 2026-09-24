package com.erp.inventory.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 盘点单实盘录入表单：按明细行回填实盘数量（可分批多次保存）。
 */
@Data
public class CheckActualForm {

    /** 实盘行（至少一行） */
    @NotEmpty(message = "至少录入一行实盘数据")
    @Valid
    private List<Line> lines;

    /** 实盘行表单 */
    @Data
    public static class Line {

        /** 盘点明细 ID（inv_check_detail.id） */
        @NotNull(message = "明细行ID不能为空")
        private Long detailId;

        /** 实盘数量（>=0，允许为 0 表示盘亏到零） */
        @NotNull(message = "实盘数量不能为空")
        @DecimalMin(value = "0", message = "实盘数量不能为负")
        private BigDecimal actualQty;
    }
}
