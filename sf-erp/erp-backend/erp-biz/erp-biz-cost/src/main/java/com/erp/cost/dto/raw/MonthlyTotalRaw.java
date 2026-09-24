package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 计价流水月度合计投影（成本趋势）。
 */
@Data
public class MonthlyTotalRaw {

    /** 期间 yyyyMM */
    private String period;

    /** 本期入库金额合计（qty>0） */
    private BigDecimal inAmount;

    /** 本期出库成本合计（qty<0 取正） */
    private BigDecimal outAmount;

    /** 销售出库成本（SAL_OUT，转主营业务成本口径） */
    private BigDecimal salOutCost;

    /** 生产领料成本（PROD_USE，转生产成本口径） */
    private BigDecimal prodUseCost;
}
