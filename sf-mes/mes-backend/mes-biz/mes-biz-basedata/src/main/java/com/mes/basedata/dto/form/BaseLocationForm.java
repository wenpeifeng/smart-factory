package com.mes.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库位新增/修改表单。
 * <p>库位编码在所属仓库内唯一；修改时仓库不可变更（库位移动属仓库管理行为，走删建）。
 */
@Data
public class BaseLocationForm {

    /** 所属仓库 ID */
    @NotNull(message = "所属仓库不能为空")
    private Long warehouseId;

    /** 库位编码（仓库内唯一） */
    @NotBlank(message = "库位编码不能为空")
    @Size(max = 40, message = "库位编码长度不能超过40")
    private String code;

    /** 库位名称 */
    @NotBlank(message = "库位名称不能为空")
    @Size(max = 50, message = "库位名称长度不能超过50")
    private String name;

    /** 容量，不能为负 */
    @DecimalMin(value = "0", message = "容量不能为负数")
    private BigDecimal capacity;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
