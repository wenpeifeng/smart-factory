package com.erp.report.service;

import com.erp.report.dto.query.FinanceSummaryQuery;
import com.erp.report.dto.query.PurchaseDetailQuery;
import com.erp.report.dto.query.SalesDetailQuery;
import com.erp.report.dto.vo.FinanceSummaryVO;
import com.erp.report.dto.vo.InventoryAgingVO;
import com.erp.report.dto.vo.ReportRowVO;

import java.util.List;

/**
 * 多维报表查询服务（销售明细 / 采购明细 / 库存库龄 / 财务汇总，全部只读聚合）。
 */
public interface ReportQueryService {

    /**
     * 销售明细聚合（数量 / 不含税额 / 参考成本 / 毛利）。
     *
     * @param query 查询参数（groupBy: material|customer|month，默认 material）
     * @return 聚合行列表
     */
    List<ReportRowVO> salesDetail(SalesDetailQuery query);

    /**
     * 采购明细聚合（数量 / 含税价税合计）。
     *
     * @param query 查询参数（groupBy: material|supplier|month，默认 material）
     * @return 聚合行列表
     */
    List<ReportRowVO> purchaseDetail(PurchaseDetailQuery query);

    /**
     * 库存库龄分析：按仓物料最后一次入库流水时间分桶（0-30/31-90/91-180/180+ 天）。
     *
     * @return 分桶汇总 + 明细
     */
    InventoryAgingVO inventoryAging();

    /**
     * 财务汇总：期间营收/成本（凭证口径，无凭证降级业务口径）+ 应收应付余额 + 现金流净额。
     *
     * @param query 查询参数（period: yyyy-MM，默认当月）
     * @return 财务汇总 VO
     */
    FinanceSummaryVO financeSummary(FinanceSummaryQuery query);
}
