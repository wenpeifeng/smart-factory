package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售订单明细行（表 sal_order_detail）。
 * <p>
 * out_qty 由销售出库单审核时累加，用于"部分出库/已完成"状态判定与超发校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_order_detail")
public class SalOrderDetail extends BaseEntity {

    /** 订单 ID（sal_order.id） */
    private Long orderId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 订购数量 */
    private BigDecimal qty;

    /** 含税单价 */
    private BigDecimal price;

    /** 金额 = qty * price（四舍五入 2 位） */
    private BigDecimal amount;

    /** 已出库数量（出库单审核时累加，恒 ≤ qty） */
    private BigDecimal outQty;

    /** 行备注 */
    private String remark;
}
