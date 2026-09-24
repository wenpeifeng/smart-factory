package com.erp.equipment.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 维修工单报修表单（创建待维修工单）。
 */
@Data
public class RepairReportForm {

    /** 故障设备 ID */
    @NotNull(message = "请选择故障设备")
    private Long deviceId;

    /** 故障描述 */
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 500, message = "故障描述长度不能超过 500")
    private String faultDesc;

    /** 报修人（文本） */
    @Size(max = 50, message = "报修人长度不能超过 50")
    private String reporter;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
