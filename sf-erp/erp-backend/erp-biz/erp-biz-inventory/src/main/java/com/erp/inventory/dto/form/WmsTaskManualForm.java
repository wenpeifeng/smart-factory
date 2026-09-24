package com.erp.inventory.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工创建 WMS 任务表单（V421）。
 * <p>
 * 仅支持 MOVE 移库 / REPLENISH 补货 / PICK 手工拣选——
 * PUTAWAY 由扫描器/单据驱动生成，不开放手工创建。
 * MOVE/REPLENISH 必填 fromLocationId + locationId（目标）；
 * PICK 必填 locationId（来源库位）+ batchNo（来源行批次，非批次物料空串）。
 */
@Data
public class WmsTaskManualForm {

    /** 任务类型：MOVE / REPLENISH / PICK */
    @NotBlank(message = "任务类型不能为空")
    @Pattern(regexp = "MOVE|REPLENISH|PICK", message = "手工任务仅支持 MOVE/REPLENISH/PICK")
    private String taskType;

    /** 仓库 ID */
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 批次号（PICK 来源行批次；MOVE/REPLENISH 批次物料必填、非批次物料空串） */
    private String batchNo;

    /** 数量（>0） */
    @NotNull(message = "数量不能为空")
    private BigDecimal qty;

    /** 调出库位 ID（MOVE/REPLENISH 必填；PICK=来源库位必填） */
    private Long fromLocationId;

    /** 目标库位 ID（MOVE/REPLENISH 必填；PUTAWAY 语义不适用） */
    private Long locationId;

    /** 备注 */
    private String remark;
}
