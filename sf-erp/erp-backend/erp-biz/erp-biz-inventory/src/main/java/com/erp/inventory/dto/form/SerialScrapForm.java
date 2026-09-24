package com.erp.inventory.dto.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 序列号报废表单（V422）：IN_STOCK → SCRAP。
 */
@Data
public class SerialScrapForm {

    /** 序列号列表 */
    @NotEmpty(message = "序列号不能为空")
    @Size(max = 500, message = "单次报废不超过500条序列号")
    private List<@Size(max = 64, message = "序列号长度不能超过64") String> serialNos;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过200字")
    private String remark;
}
