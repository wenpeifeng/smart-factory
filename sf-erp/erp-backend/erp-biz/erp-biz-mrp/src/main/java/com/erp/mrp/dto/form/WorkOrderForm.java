package com.erp.mrp.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产订单手工新增/修改表单（BOM 按成品物料自动匹配最新有效版本）。
 */
@Data
public class WorkOrderForm implements Serializable {

    /** 成品物料ID */
    @NotNull(message = "成品物料不能为空")
    private Long materialId;

    /** 计划生产数量 */
    @NotNull(message = "生产数量不能为空")
    @Positive(message = "生产数量必须大于0")
    private BigDecimal qty;

    /** 默认入库仓库 */
    private Long warehouseId;

    /** 领料方式：DIRECT 直接领料（默认）/ BACKFLUSH 倒冲（V621） */
    private String pickType;

    /** 倒冲扣料仓库（空=沿用 warehouse_id，仅 pickType=BACKFLUSH 有意义） */
    private Long backflushWarehouseId;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
