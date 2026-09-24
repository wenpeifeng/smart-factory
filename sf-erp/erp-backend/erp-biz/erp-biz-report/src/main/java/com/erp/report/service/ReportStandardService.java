package com.erp.report.service;

import com.erp.report.dto.query.ExpenseQuery;
import com.erp.report.dto.query.ExecutionQuery;
import com.erp.report.dto.query.InventoryMonthlyQuery;
import com.erp.report.dto.query.MaterialLossQuery;
import com.erp.report.dto.query.MonthlySummaryQuery;
import com.erp.report.dto.query.PiecewageQuery;
import com.erp.report.dto.query.SalesMarginQuery;
import com.erp.report.dto.query.SluggishQuery;
import com.erp.report.dto.query.SupplierPerformanceQuery;
import com.erp.report.dto.query.WorkhoursQuery;
import com.erp.report.dto.vo.ExpenseVO;
import com.erp.report.dto.vo.InventoryMonthlyVO;
import com.erp.report.dto.vo.MaterialLossVO;
import com.erp.report.dto.vo.MonthlySummaryVO;
import com.erp.report.dto.vo.PiecewageVO;
import com.erp.report.dto.vo.SalesMarginVO;
import com.erp.report.dto.vo.SluggishVO;
import com.erp.report.dto.vo.SupplierPerformanceVO;
import com.erp.report.dto.vo.WorkExecutionVO;
import com.erp.report.dto.vo.WorkhoursVO;

/**
 * 报表中心标准化·制造业报表服务（10 张，对标金蝶/用友/帆软制造业标准报表）。
 * <p>
 * 全部跨模块只读聚合：经营月报 / 销售毛利 / 供应商绩效 / 收发存月报 / 呆滞物料 /
 * 订单执行跟踪 / 报工工时 / 材料损耗 / 费用分析 / 计件工资，口径详见各方法注释
 * 与模块文档 docs/modules/report.md。
 */
public interface ReportStandardService {

    /**
     * 经营月报：当月 产量/销量/销售额/采购额/毛利率/费用合计/应收应付/库存总值
     * 一页纸指标（口径沿用驾驶舱与财务汇总）。
     *
     * @param query month=yyyy-MM（默认当月）
     */
    MonthlySummaryVO monthlySummary(MonthlySummaryQuery query);

    /**
     * 销售毛利分析：销售额 / 成本（actual_cost，历史行回退 ref_cost）/ 毛利 / 毛利率。
     *
     * @param query groupBy=customer|material（默认 material）
     */
    SalesMarginVO salesMargin(SalesMarginQuery query);

    /**
     * 供应商绩效：采购额 / 订单数 / 到货及时率 / IQC 质检合格率（无质检数据 rate=null）。
     */
    SupplierPerformanceVO supplierPerformance(SupplierPerformanceQuery query);

    /**
     * 收发存月报：物料 × 期初/入库/出库/期末（数量+金额，金额取 cost_flow），
     * 满足 期初 + 入 − 出 = 期末。
     */
    InventoryMonthlyVO inventoryMonthly(InventoryMonthlyQuery query);

    /**
     * 呆滞物料：现存量 &gt; 0 且 days 天无出库流水的物料清单（库龄/金额/占比）。
     */
    SluggishVO sluggishMaterials(SluggishQuery query);

    /**
     * 生产订单执行跟踪：工单 计划/完工/在制/完工率/超期标记。
     */
    WorkExecutionVO workOrderExecution(ExecutionQuery query);

    /**
     * 报工工时统计：工序/员工 合格数/不良数/工时（mrp_work_report）。
     *
     * @param query groupBy=operation|employee，workCenterId 可选
     */
    WorkhoursVO workhours(WorkhoursQuery query);

    /**
     * 材料损耗分析：工单 BOM 应耗 vs 实际领料 超耗率（领料无明细/应耗 0 的工单跳过）。
     */
    MaterialLossVO materialLoss(MaterialLossQuery query);

    /**
     * 费用分析：部门 × 科目 借方发生额（POSTED 凭证，损益+成本类借方科目）。
     */
    ExpenseVO expense(ExpenseQuery query);

    /**
     * 计件工资汇总：员工 × 工序 产量/工资（hr_piece_wage_detail）。
     */
    PiecewageVO piecewage(PiecewageQuery query);
}
