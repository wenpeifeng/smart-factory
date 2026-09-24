package com.erp.equipment.dto.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备状态流转表单。
 * <p>
 * 合法流转矩阵（服务端校验，报废为单向终态）：
 * 运行(1)→停机/维修中/报废；停机(2)→运行/维修中/报废；维修中(3)→运行/停机/报废；报废(4)→不可变更。
 */
@Data
public class DeviceStatusForm {

    /** 目标状态：1运行 2停机 3维修中 4报废 */
    @NotNull(message = "目标状态不能为空")
    @Min(value = 1, message = "目标状态不合法")
    @Max(value = 4, message = "目标状态不合法")
    private Integer status;
}
