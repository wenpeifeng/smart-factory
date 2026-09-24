package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务汇总 VO（按会计期间 yyyy-MM）。
 * <p>
 * 营收/成本双口径：期间存在已过账凭证（fin_voucher POSTED）时取总账科目汇总
 * （6001 主营业务收入贷方 / 6401 主营业务成本借方）；无凭证时降级为业务口径
 * （营收=已审核销售出库不含税额，成本=数量×参考成本），source 字段标注口径来源。
 */
@Data
public class FinanceSummaryVO {

    /** 会计期间，格式 yyyy-MM */
    private String period;

    /** 营业收入（元） */
    private BigDecimal revenue;

    /** 营收口径：VOUCHER=总账凭证 / BIZ=业务降级 */
    private String revenueSource;

    /** 营业成本（元） */
    private BigDecimal cost;

    /** 成本口径：VOUCHER=总账凭证 / BIZ=业务降级 */
    private String costSource;

    /** 毛利 = revenue − cost */
    private BigDecimal grossProfit;

    /** 毛利率（%，2 位小数；营收为 0 时为 0） */
    private BigDecimal grossMargin;

    /** 应收余额（当前时点快照，元） */
    private BigDecimal receivableBalance;

    /** 应付余额（当前时点快照，元） */
    private BigDecimal payableBalance;

    /** 期间现金流入合计（fin_account_flow INCOME，元） */
    private BigDecimal cashIn;

    /** 期间现金流出合计（EXPENSE，元） */
    private BigDecimal cashOut;

    /** 期间现金流净额 = cashIn − cashOut */
    private BigDecimal cashNet;
}
