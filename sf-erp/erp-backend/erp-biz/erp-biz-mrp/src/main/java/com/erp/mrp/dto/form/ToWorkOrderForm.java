package com.erp.mrp.dto.form;

import lombok.Data;

import java.io.Serializable;

/**
 * MRP 建议转生产订单表单。
 */
@Data
public class ToWorkOrderForm implements Serializable {

    /** 默认入库仓库（可空） */
    private Long warehouseId;
}
