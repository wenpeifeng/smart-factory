package com.erp.equipment.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 维修工单完工表单（维修中 → 已修复）。
 * <p>
 * 备件明细为空表示无备件消耗（纯人工维修）；有明细时逐行扣库存
 * （EQP_OUT 流水），任一行库存不足整个完工事务回滚。
 */
@Data
public class RepairCompleteForm {

    /** 故障原因 */
    @Size(max = 500, message = "故障原因长度不能超过 500")
    private String faultReason;

    /** 备件出库仓库（有备件明细时必填） */
    private Long warehouseId;

    /** 备件明细 */
    @Valid
    private List<Line> lines = new ArrayList<>();

    /** 备件明细行 */
    @Data
    public static class Line {

        /** 物料 ID */
        @NotNull(message = "请选择备件物料")
        private Long materialId;

        /** 使用数量（>0） */
        @NotNull(message = "备件数量不能为空")
        @DecimalMin(value = "0.0001", message = "备件数量必须大于 0")
        @Digits(integer = 14, fraction = 4, message = "备件数量最多 14 位整数 4 位小数")
        private BigDecimal qty;
    }
}
