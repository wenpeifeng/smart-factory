package com.erp.org.dto.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 仓库归属调整表单：把某个仓库调整归属到目标工厂。
 */
@Data
public class AssignWarehouseForm {

    /** 仓库 ID（base_warehouse.id） */
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    /** 目标工厂 ID（org_factory.id，须为启用状态） */
    @NotNull(message = "工厂不能为空")
    private Long factoryId;
}
