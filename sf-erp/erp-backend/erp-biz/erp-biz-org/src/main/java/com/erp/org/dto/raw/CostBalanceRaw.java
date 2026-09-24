package com.erp.org.dto.raw;

import lombok.Data;

import java.math.BigDecimal;

/**
 * cost_flow 约定表最新运行余额投影（cost 模块表，只读）。
 * <p>
 * 成本单价 = balance_cost / balance_qty（balance_qty<=0 时视为无成本数据，单价按 0）。
 */
@Data
public class CostBalanceRaw {

    /** 运行余额数量 */
    private BigDecimal balanceQty;

    /** 运行余额金额 */
    private BigDecimal balanceCost;
}
