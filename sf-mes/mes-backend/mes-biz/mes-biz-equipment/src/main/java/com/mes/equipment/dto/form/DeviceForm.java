package com.mes.equipment.dto.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备台账创建/修改表单。
 */
@Data
public class DeviceForm {
    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    private String name;
    /** 规格型号 */
    private String model;
    /** 所属工作中心 ID */
    private Long workcenterId;
    /** 安装位置 */
    private String location;
    /** 购置日期 */
    private LocalDate buyDate;
    /** 备注 */
    private String remark;
}
