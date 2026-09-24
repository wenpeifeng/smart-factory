package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 生产订单完工入库表单（可分次完工，良品+不良累计达到订单数量自动完工）。
 */
@Data
public class FinishForm implements Serializable {

    /** 本次完工良品数量 */
    @NotNull(message = "良品数量不能为空")
    @DecimalMin(value = "0", message = "良品数量不能为负")
    private BigDecimal goodQty;

    /** 本次不良品数量 */
    @NotNull(message = "不良品数量不能为空")
    @DecimalMin(value = "0", message = "不良品数量不能为负")
    private BigDecimal badQty = BigDecimal.ZERO;

    /** 入库仓库 */
    @NotNull(message = "入库仓库不能为空")
    private Long warehouseId;

    /** 入库批次号（批次物料成品必填——审核时由 StockService 强校验；非批次物料忽略） */
    @Size(max = 50, message = "批次号最长 50 字")
    private String batchNo;

    /** 倒冲扣料仓库（可选覆盖；空 = 工单 backflush_warehouse_id → 入库仓库，V621） */
    private Long backflushWarehouseId;
}
