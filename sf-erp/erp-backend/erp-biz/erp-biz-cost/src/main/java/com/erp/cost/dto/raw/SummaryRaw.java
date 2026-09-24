package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 收发存汇总行投影（成本流水量 → 物料收发存报表）。
 * <p>
 * 期末数量/金额由服务层计算（期末 = 期初 + 入 − 出），保证口径一致。
 */
@Data
public class SummaryRaw {

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 期初数量（期间前最后一行运行余额） */
    private BigDecimal beginQty;

    /** 期初金额 */
    private BigDecimal beginAmount;

    /** 本期入库数量 */
    private BigDecimal inQty;

    /** 本期入库金额 */
    private BigDecimal inAmount;

    /** 本期出库数量（正数列示） */
    private BigDecimal outQty;

    /** 本期出库成本（正数列示） */
    private BigDecimal outAmount;
}
