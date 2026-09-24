package com.mes.plan.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产工单新增/修改表单（仅 DRAFT 状态可修改）。
 */
@Data
public class WorkorderForm {
    /** 成品物料 ID */
    @NotNull(message = "成品物料不能为空")
    private Long materialId;
    /** 计划生产数量 */
    @NotNull(message = "生产数量不能为空")
    @Positive(message = "生产数量必须大于0")
    private BigDecimal qty;
    /** BOM ID（空=物料唯一 ACTIVE 版本） */
    private Long bomId;
    /** 工艺路线 ID（空=物料 ACTIVE 工艺路线；下达时必须存在） */
    private Long routingId;
    /** 完工入库仓库 ID */
    private Long warehouseId;
    /** 计划开始日期 */
    private LocalDate planStartDate;
    /** 计划结束日期 */
    private LocalDate planEndDate;
    /** 追踪模式：BATCH / SN */
    private String snMode;
    /** 备注 */
    private String remark;
}
