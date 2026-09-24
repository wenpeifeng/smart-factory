package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售发货通知单头（表 sal_delivery_notice，V3111）。
 * <p>
 * 状态机：0草稿 →(通知)→ 10已通知 →(明细全部核销)→ 20已出库（终态）；
 * 0/10 且未发生任何出库 →(取消)→ 30已取消（终态）。
 * 来源订单可空（无来源直发场景）；出库数量由"按出库单号核销"累加（守卫不超通知量）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_delivery_notice")
public class SalDeliveryNotice extends BaseEntity {

    /** 通知单号（FH+yyyyMMdd+流水，唯一） */
    private String noticeNo;

    /** 来源销售订单 ID（sal_order.id，空 = 无来源直发） */
    private Long orderId;

    /** 来源销售订单号（冗余展示） */
    private String orderNo;

    /** 客户 ID（base_customer.id） */
    private Long customerId;

    /** 发货仓库 ID（base_warehouse.id） */
    private Long warehouseId;

    /** 计划发货日期 */
    private LocalDate planDate;

    /** 状态：0草稿 10已通知 20已出库 30已取消 */
    private Integer status;

    /** 通知人 */
    private Long notifyBy;

    /** 通知时间 */
    private LocalDateTime notifyTime;

    /** 取消人 */
    private Long cancelBy;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 最近核销人 */
    private Long settleBy;

    /** 最近核销时间 */
    private LocalDateTime settleTime;

    /** 备注 */
    private String remark;
}
