package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 经营月报 VO（一页纸指标，口径沿用驾驶舱/财务汇总）。
 * <p>
 * 期间 [月初, 次月初) 半开区间；营收/成本优先凭证口径（6001 贷 / 6401 借），
 * 无 POSTED 凭证降级业务口径；应收/应付/库存总值为当前时点快照
 * （fin_receivable/inv_stock 无期间余额表，历史月份即当前快照，文档已注明）。
 */
@Data
public class MonthlySummaryVO {

    /** 月份 yyyy-MM */
    private String month;

    /** 完工工单数：区间内有过完工动作（update_time 落期且 finish_qty&gt;0）的工单数 */
    private Long finishedOrderCount;

    /** 产量：完工工单 finish_qty 现值合计（驾驶舱同口径） */
    private BigDecimal productionQty;

    /** 销量：已审核销售出库明细数量合计 */
    private BigDecimal salesQty;

    /** 销售额（不含税，已审核出库价税合计口径） */
    private BigDecimal salesAmount;

    /** 采购额（已审核采购入库价税合计，含税口径） */
    private BigDecimal purchaseAmount;

    /** 营业收入（凭证/业务双口径，见 source 字段） */
    private BigDecimal revenue;

    /** 营业成本 */
    private BigDecimal cost;

    /** 营收/成本口径：VOUCHER=总账凭证 / BIZ=业务降级 */
    private String revenueSource;

    /** 成本口径：VOUCHER=总账凭证 / BIZ=业务降级 */
    private String costSource;

    /** 毛利 = revenue − cost */
    private BigDecimal grossProfit;

    /** 毛利率（%，2 位 HALF_UP；营收 0 → 0） */
    private BigDecimal grossMargin;

    /** 费用合计：POSTED 凭证 66 开头损益科目（销售/管理/财务费用等）借方发生额 */
    private BigDecimal expenseTotal;

    /** 应收余额（当前时点快照） */
    private BigDecimal receivableBalance;

    /** 应付余额（当前时点快照） */
    private BigDecimal payableBalance;

    /** 库存总值（当前时点，加权价/ref_cost 口径同驾驶舱） */
    private BigDecimal inventoryValue;
}
