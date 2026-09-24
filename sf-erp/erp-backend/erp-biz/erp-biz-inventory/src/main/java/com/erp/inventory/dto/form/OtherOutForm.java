package com.erp.inventory.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 其他出库单新建表单（无来源的手工出库）。
 * 审核时库存不足会整体回滚。
 * V422：可指定出库策略 strategy（FIFO 默认 / FEFO 先到期先出 / AVG 总量扣减），
 * 行指定批次时仍强制 BATCH 精确扣该批次。
 */
@Data
public class OtherOutForm {

    /** 出库仓库 ID */
    @NotNull(message = "出库仓库不能为空")
    private Long warehouseId;

    /** 出库策略（可选，V422）：FIFO（默认）/FEFO/AVG；行带批次号时忽略本值走 BATCH */
    private String strategy;

    /** 出库原因（报损/盘亏/领用/赠品/样品/其他等） */
    @Size(max = 100, message = "出库原因不能超过100字")
    private String reason;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过200字")
    private String remark;

    /** 单据行（至少一行） */
    @NotEmpty(message = "至少录入一行物料")
    @Valid
    private List<Line> lines;

    /** 其他出库单行表单 */
    @Data
    public static class Line {

        /** 物料 ID */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 指定批次号（可选）：批次物料传值 = 指定批次出库（BATCH，不足回滚）；空 = FIFO 自动分配。
         *  非批次物料忽略。 */
        @Size(max = 50, message = "批次号不能超过50字")
        private String batchNo;

        /** 出库数量（必须大于 0） */
        @NotNull(message = "出库数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "出库数量必须大于0")
        private BigDecimal qty;

        /** 行备注 */
        @Size(max = 200, message = "行备注不能超过200字")
        private String remark;
    }
}
