package com.mes.equipment.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备点检登记表单。
 */
@Data
public class CheckForm {
    /** 设备 ID */
    @NotNull(message = "设备不能为空")
    private Long deviceId;
    /** 点检人 */
    @NotBlank(message = "点检人不能为空")
    private String checkerName;
    /** 点检结果：OK/NG */
    @NotBlank(message = "点检结果不能为空")
    private String result;
    /** 异常说明 */
    private String remark;
}
