package com.mes.equipment.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 保养/维修单创建表单。
 */
@Data
public class MaintainForm {
    /** 设备 ID */
    @NotNull(message = "设备不能为空")
    private Long deviceId;
    /** 类型：PM/REPAIR */
    @NotBlank(message = "类型不能为空")
    private String type;
    /** 计划日期 */
    private LocalDate planDate;
    /** 内容 */
    private String content;
    /** 备注 */
    private String remark;
}
