package com.erp.inventory.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 盘点单创建表单：选仓库生成库存快照。
 */
@Data
public class CheckCreateForm {

    /** 盘点仓库 ID */
    @NotNull(message = "盘点仓库不能为空")
    private Long warehouseId;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过200字")
    private String remark;
}
