package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售发货通知单明细行（表 sal_delivery_notice_item，V3111）。
 * <p>
 * out_qty 由核销接口（按出库单号）条件累加，恒 ≤ qty；
 * order_detail_id 非空时与来源订单行一一对应（余量守卫依据）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_delivery_notice_item")
public class SalDeliveryNoticeItem extends BaseEntity {

    /** 通知单 ID（sal_delivery_notice.id） */
    private Long noticeId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 通知数量 */
    private BigDecimal qty;

    /** 已出库数量（按出库单号核销累加，恒 ≤ qty） */
    private BigDecimal outQty;

    /** 来源订单明细 ID（sal_order_detail.id，空 = 直发行） */
    private Long orderDetailId;

    /** 行备注 */
    private String remark;
}
