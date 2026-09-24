package com.erp.mrp.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工艺路线新增/修改表单（与 BOM 表单同构：头 + 步骤明细整体重建）。
 */
@Data
public class RoutingForm implements Serializable {

    /** 成品物料ID */
    @NotNull(message = "成品物料不能为空")
    private Long materialId;

    /** 工艺路线版本号 */
    @NotBlank(message = "版本号不能为空")
    @Size(max = 20, message = "版本号最长20字符")
    private String version;

    /** 工序步骤明细 */
    @NotEmpty(message = "工艺路线步骤不能为空")
    @Valid
    private List<RoutingStepForm> steps;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
