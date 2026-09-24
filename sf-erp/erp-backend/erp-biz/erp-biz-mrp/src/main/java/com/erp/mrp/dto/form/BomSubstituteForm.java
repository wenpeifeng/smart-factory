package com.erp.mrp.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 子件替代料新增/修改表单（V621）。
 */
@Data
public class BomSubstituteForm implements Serializable {

    /** 替代料物料ID */
    @NotNull(message = "替代料物料不能为空")
    private Long subMaterialId;

    /** 优先级（1最高） */
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为1")
    @Max(value = 999, message = "优先级最大为999")
    private Integer priority;

    /** 用量换算比（1单位主料 = ratio 单位替代料） */
    @NotNull(message = "换算比不能为空")
    @DecimalMin(value = "0.000001", message = "换算比必须大于0")
    private BigDecimal ratio = BigDecimal.ONE;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
