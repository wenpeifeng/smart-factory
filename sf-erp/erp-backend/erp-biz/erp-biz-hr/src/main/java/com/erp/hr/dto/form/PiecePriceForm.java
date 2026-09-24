package com.erp.hr.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计件价目新增/修改表单。
 */
@Data
public class PiecePriceForm {

    /** 物料ID（base_material.id） */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 工序名称 */
    @NotBlank(message = "工序名称不能为空")
    @Size(max = 50, message = "工序名称最长 50 字")
    private String processName;

    /** 计件单价（元/件） */
    @NotNull(message = "计件单价不能为空")
    @DecimalMin(value = "0.01", message = "计件单价必须大于 0")
    private BigDecimal unitPrice;

    /** 状态：ENABLED/DISABLED（默认 ENABLED） */
    private String status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字")
    private String remark;
}
