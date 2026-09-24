package com.erp.purchase.dto.form;

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
 * 采购退货单新增/修改表单（引用已审核采购入库单退货）。
 */
@Data
public class PurReturnForm {

    /** 来源入库单 ID（必填，须为已审核状态） */
    @NotNull(message = "来源入库单不能为空")
    private Long instockId;

    /** 供应商 ID（必填，取自入库单） */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 退货仓库 ID（必填，取自入库单） */
    @NotNull(message = "退货仓库不能为空")
    private Long warehouseId;

    /** 退货日期（必填） */
    @NotNull(message = "退货日期不能为空")
    private LocalDate returnDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 退货明细（至少一行） */
    @NotEmpty(message = "退货明细不能为空")
    @Valid
    private List<PurReturnDetailForm> details;

    /**
     * 采购退货明细行（instock_detail_id 关联入库明细以校验可退数量）。
     */
    @Data
    public static class PurReturnDetailForm {

        /** 来源入库明细 ID（必填） */
        @NotNull(message = "来源入库明细不能为空")
        private Long instockDetailId;

        /** 物料 ID（必填） */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 退货数量（必填，>0，且 <= 入库数量-累计已退） */
        @NotNull(message = "退货数量不能为空")
        @DecimalMin(value = "0.0001", message = "退货数量必须大于 0")
        private BigDecimal qty;

        /** 含税单价（必填，默认取入库单价） */
        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0", message = "单价不能为负数")
        private BigDecimal price;

        /** 税率（百分数） */
        @DecimalMin(value = "0", message = "税率不能为负数")
        private BigDecimal taxRate;

        /** 行备注 */
        @Size(max = 200, message = "行备注最长 200 字")
        private String remark;
    }
}
