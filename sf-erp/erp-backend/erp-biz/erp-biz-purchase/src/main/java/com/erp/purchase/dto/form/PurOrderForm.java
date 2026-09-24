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
 * 采购订单新增/修改表单。
 * <p>
 * 金额不接收前端值：明细 amount 与主表 totalAmount 一律由服务端按
 * qty × price（HALF_UP 2 位）重算，防止篡改。
 */
@Data
public class PurOrderForm {

    /**
     * 供应商 ID（可空：MRP 建议转单等场景生成草稿时供应商待定，由用户补填后再审核；
     * 人工新增页面仍应填写）。审核时强校验非空。
     */
    private Long supplierId;

    /** 默认入库仓库 ID（可选，生成入库单时带出） */
    private Long warehouseId;

    /** 订单日期（必填） */
    @NotNull(message = "订单日期不能为空")
    private LocalDate orderDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 订单明细（至少一行） */
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<PurOrderDetailForm> details;

    /**
     * 采购订单明细行。
     */
    @Data
    public static class PurOrderDetailForm {

        /** 物料 ID（必填） */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 采购数量（必填，>0） */
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0.0001", message = "数量必须大于 0")
        private BigDecimal qty;

        /** 含税单价（必填，>=0） */
        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0", message = "单价不能为负数")
        private BigDecimal price;

        /** 税率（百分数，如 13 表示 13%，可为空默认 0） */
        @DecimalMin(value = "0", message = "税率不能为负数")
        private BigDecimal taxRate;

        /** 要求交货日期 */
        private LocalDate deliveryDate;

        /** 行备注 */
        @Size(max = 200, message = "行备注最长 200 字")
        private String remark;
    }
}
