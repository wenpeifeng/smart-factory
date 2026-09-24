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
 * BOM 新增/修改表单（Entity 不直接作入参，防 mass assignment）。
 */
@Data
public class BomForm implements Serializable {

    /** 成品物料ID */
    @NotNull(message = "成品物料不能为空")
    private Long materialId;

    /** BOM版本号 */
    @NotBlank(message = "版本号不能为空")
    @Size(max = 20, message = "版本号最长20字符")
    private String version;

    /** 子件明细 */
    @NotEmpty(message = "BOM子件不能为空")
    @Valid
    private List<BomItemForm> items;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
