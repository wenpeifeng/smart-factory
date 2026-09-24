package com.erp.mrp.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 主生产计划 MPS 新增/修改表单。
 */
@Data
public class MpsForm implements Serializable {

    /** 成品物料ID */
    @NotNull(message = "成品物料不能为空")
    private Long materialId;

    /** 计划生产数量 */
    @NotNull(message = "计划数量不能为空")
    @Positive(message = "计划数量必须大于0")
    private BigDecimal qty;

    /** 计划开工日期 */
    @NotNull(message = "开工日期不能为空")
    private LocalDate startDate;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
