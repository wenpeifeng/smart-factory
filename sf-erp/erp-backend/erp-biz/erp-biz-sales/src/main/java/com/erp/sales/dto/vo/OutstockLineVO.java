package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 出库单核销匹配行：出库单按（订单明细, 物料）分组的数量，用于通知核销匹配。
 */
@Data
public class OutstockLineVO {

    /** 出库单引用的订单明细 ID（可空） */
    private Long orderDetailId;

    /** 物料 ID */
    private Long materialId;

    /** 出库数量合计（同订单明细+物料多行合并） */
    private BigDecimal qty;
}
