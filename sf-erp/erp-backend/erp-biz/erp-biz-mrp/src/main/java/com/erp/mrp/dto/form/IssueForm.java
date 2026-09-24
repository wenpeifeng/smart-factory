package com.erp.mrp.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 生产领料单创建表单：items 为空时按 BOM 自动带出全部子件（实领=应领）。
 */
@Data
public class IssueForm implements Serializable {

    /** 生产订单ID */
    @NotNull(message = "生产订单不能为空")
    private Long workOrderId;

    /** 发料仓库 */
    @NotNull(message = "发料仓库不能为空")
    private Long warehouseId;

    /** 领料明细（可空=按BOM全量带出） */
    @Valid
    private List<IssueItemForm> items;

    /** 备注 */
    @Size(max = 200, message = "备注最长200字符")
    private String remark;
}
