package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 委外加工单新增/修改表单（发料明细按 BOM 自动带出）。
 */
@Data
public class OutsourceForm implements Serializable {

    /** 供应商ID */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 加工后成品物料ID */
    @NotNull(message = "成品物料不能为空")
    private Long materialId;

    /** 委外加工数量 */
    @NotNull(message = "委外数量不能为空")
    @Positive(message = "委外数量必须大于0")
    private BigDecimal qty;

    /** 发料仓库 */
    private Long issueWarehouseId;

    /** 完工入库仓库 */
    private Long inWarehouseId;

    /** 加工费单价（元/件） */
    @NotNull(message = "加工费单价不能为空")
    @DecimalMin(value = "0", message = "加工费单价不能为负")
    private BigDecimal feeUnitPrice;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
