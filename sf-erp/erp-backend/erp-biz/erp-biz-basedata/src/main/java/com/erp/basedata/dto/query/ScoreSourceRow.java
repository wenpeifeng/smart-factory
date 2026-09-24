package com.erp.basedata.dto.query;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商评分来源聚合行（V141）。
 * <p>
 * 四类来源查询（订单/入库/QC/退货）按 supplier_id GROUP BY 后各取所需列，
 * 未选到的列为 NULL，由服务端按 supplierId 合并。
 */
@Data
public class ScoreSourceRow {

    /** 供应商 ID */
    private Long supplierId;

    /** 订单数（sumOrders） */
    private Integer orderCount;

    /** 交易额（sumOrders） */
    private BigDecimal orderAmount;

    /** 入库单数（sumInstocks） */
    private Integer instockCount;

    /** 有交期入库单数（sumInstocks，准时率分母） */
    private Integer dueCount;

    /** 准时入库单数（sumInstocks，准时率分子） */
    private Integer ontimeCount;

    /** 入库数量（sumInstocks，退货率分母） */
    private BigDecimal instockQty;

    /** IQC 合格数量（sumQcInspects） */
    private BigDecimal qualifiedQty;

    /** IQC 不合格数量（sumQcInspects） */
    private BigDecimal unqualifiedQty;

    /** 退货数量（sumReturns，退货率分子） */
    private BigDecimal returnQty;
}
