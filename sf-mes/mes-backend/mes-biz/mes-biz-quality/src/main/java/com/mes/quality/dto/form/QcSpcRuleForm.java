package com.mes.quality.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * SPC 规则创建/修改表单。
 */
@Data
public class QcSpcRuleForm {
    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;
    /** 检验项目名称 */
    @NotBlank(message = "检验项目不能为空")
    private String itemName;
    /** 控制上限 */
    @NotNull(message = "UCL不能为空")
    private BigDecimal ucl;
    /** 控制下限 */
    @NotNull(message = "LCL不能为空")
    private BigDecimal lcl;
    /** 目标值（空=中点） */
    private BigDecimal target;
    /** 准则开关（null=默认：R4 停用其余启用） */
    private Integer ruleR1;
    private Integer ruleR2;
    private Integer ruleR3;
    private Integer ruleR4;
    private Integer ruleR5;
    /** 备注 */
    private String remark;
}
