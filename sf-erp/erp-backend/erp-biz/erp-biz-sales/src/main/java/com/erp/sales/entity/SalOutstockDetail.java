package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售出库单明细行（表 sal_outstock_detail）。
 * <p>
 * returned_qty 由销售退货单审核时累加，作为出库行可退数量的依据（qty - returned_qty）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_outstock_detail")
public class SalOutstockDetail extends BaseEntity {

    /** 出库单 ID（sal_outstock.id） */
    private Long outId;

    /** 来源订单行 ID（sal_order_detail.id） */
    private Long orderDetailId;

    /** 物料 ID */
    private Long materialId;

    /** 出库数量 */
    private BigDecimal qty;

    /** 含税单价（取订单行单价） */
    private BigDecimal price;

    /** 金额 = qty * price（四舍五入 2 位） */
    private BigDecimal amount;

    /**
     * 出库实际成本（V2101）：审核时按 CostService.onStockOut 返回的移动加权出库成本回写；
     * NULL=改造前的历史出库（销售统计回退 base_material.ref_cost 估算）。
     */
    private BigDecimal actualCost;

    /** 已被退货数量（退货单审核时累加，恒 ≤ qty） */
    private BigDecimal returnedQty;

    /** 行备注 */
    private String remark;
}
