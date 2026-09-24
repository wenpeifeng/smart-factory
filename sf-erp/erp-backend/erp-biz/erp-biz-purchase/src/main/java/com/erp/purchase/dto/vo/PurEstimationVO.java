package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购暂估应付单列表/详情 VO。
 */
@Data
public class PurEstimationVO {

    private Long id;

    /** 暂估单号（ZG...） */
    private String estimationNo;

    /** 入库单 ID */
    private Long instockId;

    /** 入库单号（RK...） */
    private String instockNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称（JOIN base_supplier） */
    private String supplierName;

    /** 暂估金额（不含税） */
    private BigDecimal amount;

    /** 累计已冲回金额 */
    private BigDecimal reversedAmount;

    /** 未冲回余额 = amount - reversedAmount */
    private BigDecimal openAmount;

    /** 状态：0暂估中 10已冲回 20部分冲回 */
    private Integer status;

    /** 暂估期间 yyyyMM */
    private String period;

    /** 最近冲回发票号 */
    private String invoiceNo;

    private String remark;

    private java.time.LocalDateTime createTime;
}
