package com.erp.cost.service;

import com.erp.cost.dto.vo.SettleSummaryVO;
import com.erp.cost.dto.vo.TrendPointVO;

import java.util.List;

/**
 * 成本分析服务：物料收发存汇总 + 月度成本/存货余额趋势。
 */
public interface CostAnalysisService {

    /** 物料收发存汇总（按期间，GET /cost/analysis/materials?period） */
    SettleSummaryVO materialSummary(String period);

    /**
     * 月度趋势（GET /cost/analysis/trend?from&to）：主营业务成本（SAL_OUT）、
     * 生产领料成本（PROD_USE）、入/出库金额、期末存货余额（期初+逐月收支推算）。
     */
    List<TrendPointVO> trend(String from, String to);
}
