package com.mes.kanban.dto.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 安灯呼叫表单（工作台/移动端发起）。
 */
@Data
public class AndonForm {
    /** 工作中心 ID */
    private Long workcenterId;
    /** 设备 ID（设备类异常） */
    private Long deviceId;
    /** 类型：DEVICE/MATERIAL/QUALITY/OTHER */
    @NotBlank(message = "类型不能为空")
    private String type;
    /** 呼叫人 */
    @NotBlank(message = "呼叫人不能为空")
    private String callerName;
    /** 异常描述 */
    @NotBlank(message = "异常描述不能为空")
    private String message;
}
