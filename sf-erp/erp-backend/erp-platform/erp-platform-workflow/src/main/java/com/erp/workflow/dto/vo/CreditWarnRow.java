package com.erp.workflow.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预警扫描行 —— 客户信用超限（base_customer.credit_limit vs fin_receivable 未收回，只读投影）。
 * 未收回应收 = SUM(amount - settled_amount)（status != 'SETTLED'）。
 */
@Data
public class CreditWarnRow {

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 信用额度 */
    private BigDecimal creditLimit;

    /** 未收回应收合计 */
    private BigDecimal outstandingAmount;
}
