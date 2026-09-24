package com.erp.finance.service;

import com.erp.finance.dto.vo.BalanceSheetVO;
import com.erp.finance.dto.vo.IncomeStatementVO;
import com.erp.finance.dto.vo.PartnerAgingVO;

import java.time.LocalDate;

/**
 * 财务三大报表服务：资产负债表 / 利润表（科目余额取数，fin_report_mapping 映射驱动）
 * + 应收应付账龄分析。
 * <p>
 * 数据源全部为已过账（POSTED）凭证分录聚合与未结清应收应付行，只读无副作用。
 */
public interface FinReportService {

    /**
     * 资产负债表：期初/期末两列，勾稽断言 资产总计=负债+所有者权益（tolerance 0.01）。
     *
     * @param period 会计期间 yyyy-MM（空=当月）
     * @param bookId 账套 ID（空=当前账套）
     * @return 报表（含 balanced/diff/口径提示）
     */
    BalanceSheetVO balanceSheet(String period, Long bookId);

    /**
     * 利润表：本月数 + 本年累计数。
     *
     * @param period 会计期间 yyyy-MM（空=当月；from/to 均空时本月列=该自然月）
     * @param from   本期区间起（含，可空；显式传入时覆盖 period 的自然月起点）
     * @param to     本期区间止（含，可空）
     * @param bookId 账套 ID（空=当前账套）
     * @return 报表（含营业利润/利润总额/净利润计算行）
     */
    IncomeStatementVO incomeStatement(String period, LocalDate from, LocalDate to, Long bookId);

    /**
     * 应收账龄分析（客户 × 账龄分桶，未结算余额=amount−settled_amount）。
     *
     * @param asOf   账龄计算时点（空=今天）
     * @param bookId 账套 ID（空=当前账套）
     * @return 分桶汇总 + 客户行 + 合计行
     */
    PartnerAgingVO arAging(LocalDate asOf, Long bookId);

    /**
     * 应付账龄分析（供应商 × 账龄分桶，与应收对称）。
     *
     * @param asOf   账龄计算时点（空=今天）
     * @param bookId 账套 ID（空=当前账套）
     * @return 分桶汇总 + 供应商行 + 合计行
     */
    PartnerAgingVO apAging(LocalDate asOf, Long bookId);
}
