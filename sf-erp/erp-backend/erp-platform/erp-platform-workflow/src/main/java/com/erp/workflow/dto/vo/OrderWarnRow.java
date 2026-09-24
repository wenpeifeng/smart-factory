package com.erp.workflow.dto.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 预警扫描行 —— 生产订单计划结束超期（mrp_work_order，只读投影）。
 */
@Data
public class OrderWarnRow {

    /** 生产订单 ID */
    private Long orderId;

    /** 生产订单号 */
    private String orderNo;

    /** 订单状态 */
    private String status;

    /** 计划结束日期 */
    private LocalDate planEndDate;
}
