package com.erp.mrp.dto.form;

import lombok.Data;

import java.io.Serializable;

/**
 * MRP 采购建议转采购订单表单（mrp → purchase 接线）。
 * <p>
 * 供应商可空 = 草稿待用户补填后再审核（采购侧审核前强校验供应商非空）；
 * 单价 MRP 不感知，固定按 0 生成草稿，由采购员补填。
 */
@Data
public class ToPurchaseForm implements Serializable {

    /** 供应商 ID（可空，空=草稿待补填） */
    private Long supplierId;

    /** 默认入库仓库（可空） */
    private Long warehouseId;
}
