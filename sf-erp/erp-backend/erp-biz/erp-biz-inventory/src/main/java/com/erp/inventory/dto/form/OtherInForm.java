package com.erp.inventory.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 其他入库单新建表单（无来源的手工入库）。
 * V422：行可带效期 expiryDate（批次行写入 expiry_date，FEFO 出库排序依据）。
 */
@Data
public class OtherInForm {

    /** 入库仓库 ID */
    @NotNull(message = "入库仓库不能为空")
    private Long warehouseId;

    /** 入库原因（领用退回/盘盈/赠品/样品/其他等） */
    @Size(max = 100, message = "入库原因不能超过100字")
    private String reason;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过200字")
    private String remark;

    /** 单据行（至少一行） */
    @NotEmpty(message = "至少录入一行物料")
    @Valid
    private List<Line> lines;

    /** 其他入库单行表单 */
    @Data
    public static class Line {

        /** 物料 ID */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 批次号（批次物料审核时必填，此处不强制——校验在审核过账时按物料标志执行） */
        @Size(max = 50, message = "批次号不能超过50字")
        private String batchNo;

        /** 效期（可选，V422）：批次行写入 expiry_date；空=按物料保质期自动推算 */
        private LocalDate expiryDate;

        /** 入库数量（必须大于 0） */
        @NotNull(message = "入库数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "入库数量必须大于0")
        private BigDecimal qty;

        /** 行备注 */
        @Size(max = 200, message = "行备注不能超过200字")
        private String remark;
    }
}
