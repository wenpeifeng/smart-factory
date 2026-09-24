package com.erp.report.service;

import com.erp.report.dto.vo.DashboardVO;

/**
 * 经营驾驶舱聚合服务（跨模块只读）。
 */
public interface ReportDashboardService {

    /**
     * 汇总驾驶舱全部指标：本期（当月）与上月环比基础指标、近 6 个月走势、
     * 仓库库存价值分布、当月销售 TOP 物料。
     *
     * @return 驾驶舱聚合 VO（金额/数量全部 BigDecimal）
     */
    DashboardVO dashboard();
}
