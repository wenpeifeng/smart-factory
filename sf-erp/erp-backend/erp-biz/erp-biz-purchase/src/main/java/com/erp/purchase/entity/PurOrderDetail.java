package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单明细实体（pur_order_detail）。
 * <p>
 * 金额不变量：amount = qty × price（含税单价），服务端 HALF_UP 保留 2 位计算，
 * 前端传入的 amount 一律忽略。received_qty 由入库/退货审核回写，禁止手工修改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurOrderDetail extends BaseEntity {

    /** 采购订单 ID（pur_order.id） */
    private Long orderId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 采购数量（>0） */
    private BigDecimal qty;

    /** 含税单价 */
    private BigDecimal price;

    /** 税率（百分数，如 13.00 表示 13%） */
    private BigDecimal taxRate;

    /** 含税金额 = qty × price，HALF_UP 2 位 */
    private BigDecimal amount;

    /** 要求交货日期 */
    private LocalDate deliveryDate;

    /** 累计已入库数量（入库审核 +，退货审核 -，用于订单完成判断） */
    private BigDecimal receivedQty;

    /** 备注 */
    private String remark;
}
