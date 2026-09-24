package com.erp.mrp.dto.form;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * MRP 采购建议批量转采购订单表单：同供应商（或供应商均待定）的建议
 * 合并生成一张采购订单草稿，每条建议一行明细。
 */
@Data
public class ToPurchaseBatchForm implements Serializable {

    /** 建议ID集合（至少一条，须全部为 PURCHASE+PENDING） */
    @NotEmpty(message = "建议ID不能为空")
    private List<Long> ids;

    /** 供应商 ID（可空，空=草稿待补填；批量合并要求同一供应商，由调用方保证/选择） */
    private Long supplierId;

    /** 默认入库仓库（可空） */
    private Long warehouseId;
}
