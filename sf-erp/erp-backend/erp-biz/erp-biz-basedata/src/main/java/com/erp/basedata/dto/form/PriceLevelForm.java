package com.erp.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售等级价新增/修改表单（物料 × 客户等级）。
 */
@Data
public class PriceLevelForm {

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 客户等级 */
    @NotBlank(message = "客户等级不能为空")
    @Pattern(regexp = "[ABC]", message = "客户等级只允许 A/B/C")
    private String level;

    /** 等级单价（不含税） */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "单价必须大于0")
    private BigDecimal price;

    /** 状态：1-启用 0-停用（空默认 1） */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
