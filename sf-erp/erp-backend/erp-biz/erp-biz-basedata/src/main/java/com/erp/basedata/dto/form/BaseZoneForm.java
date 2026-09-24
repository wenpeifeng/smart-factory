package com.erp.basedata.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 库区新增/修改表单（V421）。
 * <p>库区编码在所属仓库内唯一；修改时仓库不可变更。
 */
@Data
public class BaseZoneForm {

    /** 所属仓库 ID */
    @NotNull(message = "所属仓库不能为空")
    private Long warehouseId;

    /** 库区编码（仓库内唯一） */
    @NotBlank(message = "库区编码不能为空")
    @Size(max = 40, message = "库区编码长度不能超过40")
    private String code;

    /** 库区名称 */
    @NotBlank(message = "库区名称不能为空")
    @Size(max = 50, message = "库区名称长度不能超过50")
    private String name;

    /** 库区类型（枚举值，服务端再校验） */
    @NotBlank(message = "库区类型不能为空")
    @Pattern(regexp = "RECEIVE|STORAGE|PICKING|SHIP|DEFECT",
            message = "库区类型必须是 RECEIVE/STORAGE/PICKING/SHIP/DEFECT 之一")
    private String zoneType;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
