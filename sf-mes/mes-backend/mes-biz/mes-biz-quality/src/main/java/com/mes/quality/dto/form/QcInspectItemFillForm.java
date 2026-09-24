package com.mes.quality.dto.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 检验单项目实测值录入表单（逐行）。
 */
@Data
public class QcInspectItemFillForm {
    /** 检验单项目 ID */
    @jakarta.validation.constraints.NotNull(message = "项目ID不能为空")
    private Long itemId;
    /** 实测值 */
    @NotBlank(message = "实测值不能为空")
    private String actualValue;
}
