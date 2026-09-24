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
 * 仓库调拨表单（即时执行，不做单据留痕；流水留痕为两条 MOVE，
 * 一出一入共用同一个调拨单号 TR+yyyyMMdd+4位流水）。
 */
@Data
public class TransferForm {

    /** 调出仓库 ID */
    @NotNull(message = "调出仓库不能为空")
    private Long fromWarehouseId;

    /** 调入仓库 ID */
    @NotNull(message = "调入仓库不能为空")
    private Long toWarehouseId;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过200字")
    private String remark;

    /** 调拨行（至少一行） */
    @NotEmpty(message = "至少录入一行调拨物料")
    @Valid
    private List<Line> lines;

    /** 调拨行表单 */
    @Data
    public static class Line {

        /** 物料 ID */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 调拨数量（必须大于 0） */
        @NotNull(message = "调拨数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "调拨数量必须大于0")
        private BigDecimal qty;
    }
}
