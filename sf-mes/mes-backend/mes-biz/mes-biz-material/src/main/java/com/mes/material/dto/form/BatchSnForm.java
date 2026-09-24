package com.mes.material.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 批次/SN 档案登记表单。
 */
@Data
public class BatchSnForm {
    /** 对象类型：BATCH / SN */
    @NotBlank(message = "对象类型不能为空")
    private String objType;
    /** 批次号或 SN（可空=系统生成） */
    private String objNo;
    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;
    /** 来源类型：PICKING/PRODUCE/PURCHASE */
    private String sourceType;
    /** 来源单号 */
    private String sourceNo;
    /** 工单 ID */
    private Long workorderId;
    /** 数量 */
    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    private BigDecimal qty;
    /** 供应商 ID */
    private Long supplierId;
    /** 父级 SN */
    private String parentSn;
}
