package com.erp.equipment.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 维修工单接单表单（待维修 → 维修中）。
 */
@Data
public class RepairAcceptForm {

    /** 维修人（文本） */
    @NotBlank(message = "维修人不能为空")
    @Size(max = 50, message = "维修人长度不能超过 50")
    private String repairer;
}
