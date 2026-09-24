package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售发货通知单表头 VO（分页/详情通用，含明细数量汇总）。
 */
@Data
public class SalNoticeVO {

    /** 通知单 ID */
    private Long id;

    /** 通知单号（FH+yyyyMMdd+流水） */
    private String noticeNo;

    /** 来源销售订单 ID（空 = 无来源直发） */
    private Long orderId;

    /** 来源销售订单号 */
    private String orderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码（JOIN base_customer） */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 仓库 ID */
    private Long warehouseId;

    /** 仓库名称（JOIN base_warehouse） */
    private String warehouseName;

    /** 计划发货日期 */
    private LocalDate planDate;

    /** 状态：0草稿 10已通知 20已出库 30已取消 */
    private Integer status;

    /** 通知数量合计（明细 SUM） */
    private BigDecimal totalQty;

    /** 已出库数量合计（明细 SUM） */
    private BigDecimal totalOutQty;

    /** 通知时间 */
    private LocalDateTime notifyTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 最近核销时间 */
    private LocalDateTime settleTime;

    /** 备注（约定：出库单备注可记 FH 单号用于核销对账） */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
