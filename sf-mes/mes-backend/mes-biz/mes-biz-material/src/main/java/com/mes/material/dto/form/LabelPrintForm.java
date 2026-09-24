package com.mes.material.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 标签打印登记表单。
 */
@Data
public class LabelPrintForm {
    /** 对象类型：BATCH/SN/WO */
    @NotBlank(message = "对象类型不能为空")
    private String objType;
    /** 对象 ID */
    @NotNull(message = "对象ID不能为空")
    private Long objId;
    /** 对象号 */
    @NotBlank(message = "对象号不能为空")
    private String objNo;
    /** 标签模板 */
    @NotBlank(message = "标签模板不能为空")
    private String template;
    /** 打印份数（默认 1） */
    private Integer copies;
    /** 打印人 */
    private String printer;
}
