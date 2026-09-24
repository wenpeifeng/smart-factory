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
 * 采购入库单新增/修改表单（从采购订单生成，明细可改实收数量但禁止超收）。
 */
@Data
public class PurInstockForm {

    /** 来源采购订单 ID（必填，须为已审核/部分入库状态） */
    @NotNull(message = "来源采购订单不能为空")
    private Long orderId;

    /** 供应商 ID（必填） */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 入库仓库 ID（必填） */
    @NotNull(message = "入库仓库不能为空")
    private Long warehouseId;

    /** 入库日期（必填） */
    @NotNull(message = "入库日期不能为空")
    private LocalDate instockDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 入库明细（至少一行） */
    @NotEmpty(message = "入库明细不能为空")
    @Valid
    private List<PurInstockDetailForm> details;

    /**
     * 采购入库明细行（order_detail_id 关联订单明细以回写累计入库数）。
     */
    @Data
    public static class PurInstockDetailForm {

        /** 来源订单明细 ID（必填） */
        @NotNull(message = "来源订单明细不能为空")
        private Long orderDetailId;

        /** 物料 ID（必填） */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 入库批次号（批次物料必填——审核时由 StockService 强校验；非批次物料忽略） */
        @Size(max = 50, message = "批次号最长 50 字")
        private String batchNo;

        /** 实收数量（必填，>0，且 <= 订单数量-累计已入库） */
        @NotNull(message = "实收数量不能为空")
        @DecimalMin(value = "0.0001", message = "实收数量必须大于 0")
        private BigDecimal qty;

        /** 含税单价（必填，默认取订单单价，可改） */
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
