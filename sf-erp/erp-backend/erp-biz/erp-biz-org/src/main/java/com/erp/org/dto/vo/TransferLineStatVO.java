package com.erp.org.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 调拨线路统计行（期间内已审核调拨按 调出工厂→调入工厂 汇总）。
 */
@Data
public class TransferLineStatVO {

    /** 调出工厂 ID/名称 */
    private Long fromFactoryId;
    private String fromFactoryName;

    /** 调入工厂 ID/名称 */
    private Long toFactoryId;
    private String toFactoryName;

    /** 调拨单数 */
    private Long transferCount;

    /** 数量合计 */
    private BigDecimal totalQty;

    /** 内部结算金额合计 */
    private BigDecimal totalAmount;
}
