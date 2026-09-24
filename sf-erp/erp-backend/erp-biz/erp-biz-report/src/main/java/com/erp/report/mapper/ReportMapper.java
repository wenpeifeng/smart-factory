package com.erp.report.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表中心聚合 Mapper（XML：resources/mapper/report/ReportMapper.xml）。
 * <p>
 * 职责：跨模块<b>只读</b>聚合统计。本模块不拥有任何业务表，全部 SQL 按各模块
 * 建表脚本（V1xx/V2xx/V3xx/V4xx/V5xx/V6xx）的约定结构直接 JOIN 查询，
 * 不 import 任何业务模块代码（与 sales/purchase 等模块的跨模块约定一致）。
 * <p>
 * 统一约定：
 * <ul>
 *   <li>所有查询过滤 deleted = 0；单据口径只统计「已审核」状态；</li>
 *   <li>金额 DECIMAL → BigDecimal；日期区间一律半开区间 [from, to)；</li>
 *   <li>SUM 结果用 COALESCE 兜底 0，避免 NULL 传到前端。</li>
 * </ul>
 */
@Mapper
public interface ReportMapper {

    // ==================== 驾驶舱：本期/环比基础指标 ====================

    /**
     * 销售额：已审核销售出库单价税合计（sal 表无税额列，amount 即不含税口径）。
     *
     * @param from 开始日期（含）
     * @param to   结束日期（不含）
     * @return SUM(total_amount)，无单据返回 0
     */
    BigDecimal sumSalesAmount(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 采购额：已审核采购入库单价税合计（含税口径，pur_instock.total_amount）。
     */
    BigDecimal sumPurchaseAmount(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 销售业务成本（财务汇总降级口径）：已审核出库明细数量 × base_material.ref_cost。
     */
    BigDecimal sumSalesRefCost(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 应收余额（当前时点快照）：SUM(amount - settled_amount)，红字应收负金额自然冲减。
     */
    BigDecimal sumReceivableBalance();

    /**
     * 应付余额（当前时点快照）：SUM(amount - settled_amount)。
     */
    BigDecimal sumPayableBalance();

    /**
     * 表存在性检查（table_schema 取当前连接库）。
     * 用于 wf_task 等并行模块约定表未上线时的优雅降级（无表 → 指标计 0）。
     *
     * @param table 表名
     * @return 存在返回 1，否则 0
     */
    /** information_schema 探测不走租户过滤（该库无 tenant_id 列） */
    @InterceptorIgnore(tenantLine = "true")
    int countTableExists(@Param("table") String table);

    /**
     * 待办审批数：wf_task 中 status='PENDING' 的任务数（约定表，未上线时由调用方降级为 0）。
     */
    Long countPendingTasks();

    /**
     * 库存预警数：与 inventory 模块 /inventory/warnings 同口径 ——
     * base_material LEFT JOIN inv_stock（行 = 仓库×物料），从未有库存行的物料按 0 参与，
     * COALESCE(qty,0) &lt; safety_stock 即预警。
     */
    Long countStockWarnings();

    /**
     * 生产完工数：mrp_work_order 无完工日期字段，以「区间内有过完工动作
     * （update_time 落入区间）」的订单 finish_qty 现值合计近似；部分完工
     * （PRODUCING）的良品数同样计入本期（口径见模块文档）。
     *
     * @param from 开始时间（含，0 点）
     * @param to   结束时间（不含，次月 1 日 0 点）
     */
    BigDecimal sumWorkOrderFinishQty(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 近 N 个月销售月度额：按 out_date 分月汇总已审核出库。
     *
     * @param from 起始月 1 日（含）
     * @return 元素 {month=yyyy-MM, amount=金额}，无单据的月份不出现（Java 补零）
     */
    List<Map<String, Object>> monthlySalesTrend(@Param("from") LocalDate from);

    /**
     * 近 N 个月采购月度额：按 instock_date 分月汇总已审核入库。
     */
    List<Map<String, Object>> monthlyPurchaseTrend(@Param("from") LocalDate from);

    /**
     * 库存价值明细：inv_stock × 最近加权价。
     * 加权价 = 全部已审核采购入库的 SUM(qty×price)/SUM(qty)；
     * 无采购记录的物料回退 base_material.ref_cost（与销售统计成本口径一致），仍无则 0。
     *
     * @return 元素 {warehouseId, warehouseName, materialId, materialCode, materialName,
     *         qty, price, value}，qty&gt;0 的库存行
     */
    List<Map<String, Object>> inventoryValueRows();

    /**
     * 当月销售 TOP 物料：已审核出库明细按物料汇总销售额降序。
     *
     * @param from 开始日期（含）
     * @param to   结束日期（不含）
     * @param limit 取前 N 名
     * @return 元素 {materialId, materialCode, materialName, amount, qty}
     */
    List<Map<String, Object>> salesTopMaterials(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to,
                                                @Param("limit") int limit);

    // ==================== 多维报表：销售明细 ====================

    /**
     * 销售明细按物料聚合（数量/不含税额/参考成本/毛利），销售额降序。
     */
    List<Map<String, Object>> salesDetailByMaterial(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to,
                                                    @Param("customerId") Long customerId);

    /**
     * 销售明细按客户聚合，销售额降序。
     */
    List<Map<String, Object>> salesDetailByCustomer(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to,
                                                    @Param("customerId") Long customerId);

    /**
     * 销售明细按月聚合，月份升序。
     */
    List<Map<String, Object>> salesDetailByMonth(@Param("from") LocalDate from,
                                                 @Param("to") LocalDate to,
                                                 @Param("customerId") Long customerId);

    // ==================== 多维报表：采购明细 ====================

    /**
     * 采购明细按物料聚合（数量/含税价税合计），金额降序。
     */
    List<Map<String, Object>> purchaseDetailByMaterial(@Param("from") LocalDate from,
                                                       @Param("to") LocalDate to,
                                                       @Param("supplierId") Long supplierId);

    /**
     * 采购明细按供应商聚合，金额降序。
     */
    List<Map<String, Object>> purchaseDetailBySupplier(@Param("from") LocalDate from,
                                                       @Param("to") LocalDate to,
                                                       @Param("supplierId") Long supplierId);

    /**
     * 采购明细按月聚合，月份升序。
     */
    List<Map<String, Object>> purchaseDetailByMonth(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to,
                                                    @Param("supplierId") Long supplierId);

    // ==================== 多维报表：库存库龄 ====================

    /**
     * 库龄明细：库存行 × 该仓物料最后一次入库流水时间（inv_movement 正数流水的
     * MAX(create_time)）× 加权价。分桶在 Java 层完成（口径见模块文档）。
     *
     * @return 元素 {warehouseId, warehouseName, materialId, materialCode, materialName,
     *         qty, price, value, lastInTime(yyyy-MM-dd HH:mm), ageDays(可空)}
     */
    List<Map<String, Object>> inventoryAgingRows();

    // ==================== 多维报表：财务汇总 ====================

    /**
     * 期间内已过账凭证是否存在（决定营收/成本取凭证口径还是业务降级口径）。
     */
    int countPostedVouchers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 凭证科目发生额：POSTED 凭证分录中科目编码前缀匹配的借方或贷方合计。
     *
     * @param side   'DEBIT'=借方合计，其他=贷方合计
     * @param prefix 科目编码前缀（6001 主营业务收入 / 6401 主营业务成本）
     */
    BigDecimal sumVoucherSubjectAmount(@Param("side") String side,
                                       @Param("prefix") String prefix,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * 期间现金流：账户流水按 biz_date 落入期间，分收入/支出合计。
     *
     * @return 元素 {inAmount, outAmount}
     */
    Map<String, Object> cashFlowTotal(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ==================== 经营月报 ====================

    /**
     * 期间销量（数量口径）：已审核销售出库明细数量合计。
     */
    BigDecimal sumSalesQty(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 期间完工工单数：区间内有过完工动作（update_time 落期且 finish_qty&gt;0）的工单数
     * （与驾驶舱生产完工数同口径的时间锚）。
     */
    Long countFinishedWorkOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ==================== 销售毛利分析 ====================

    /**
     * 销售毛利按物料聚合：销售额 / 成本 / 毛利。
     * 成本 = COALESCE(明细 actual_cost, qty × base_material.ref_cost)（历史行回退 ref_cost）。
     */
    List<Map<String, Object>> salesMarginByMaterial(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /**
     * 销售毛利按客户聚合，口径同 {@link #salesMarginByMaterial}。
     */
    List<Map<String, Object>> salesMarginByCustomer(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    // ==================== 供应商绩效 ====================

    /**
     * 供应商绩效行：采购订单数/金额（status 10/20/30）+ 入库金额/到货及时行数
     * （已审核入库明细行 instock_date ≤ COALESCE(订单行 delivery_date, 订单 order_date)）+
     * IQC 质检抽样/合格数量（qc_inspect source_type='pur_instock'）。
     *
     * @return 元素 {supplierId, supplierCode, supplierName, orderCount, orderAmount,
     *         instockAmount, receiptLineCount, onTimeLineCount, sampleQty, qualifiedQty}
     */
    List<Map<String, Object>> supplierPerformanceRows(@Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    // ==================== 收发存月报 ====================

    /**
     * 收发存月报行（物料粒度）：期初 = period &lt; 月累计，入库/出库 = 当月正/负流水，
     * 全部含数量与金额（金额取 cost_flow）；期末在 Java 层 = 期初 + 入 − 出。
     *
     * @param period 期间 yyyyMM
     * @return 元素 {materialId, materialCode, materialName, unitName,
     *         beginQty, beginAmount, inQty, inAmount, outQty, outAmount}
     */
    List<Map<String, Object>> inventoryMonthlyRows(@Param("period") String period);

    // ==================== 呆滞物料 ====================

    /**
     * 呆滞物料行（物料粒度跨仓合计）：现存量 &gt; 0 且 days 天内无出库方向流水
     * （无任何出库流水视为呆滞）；库龄锚点 = 最后一次入库流水。
     *
     * @param days 呆滞天数阈值
     * @return 元素 {materialId, materialCode, materialName, unitName, qty, value,
     *         lastInTime, ageDays, lastOutTime, daysSinceOut}
     */
    List<Map<String, Object>> sluggishMaterialRows(@Param("days") int days);

    // ==================== 订单执行跟踪 ====================

    /**
     * 工单执行行：计划量/完工量/状态/计划起止 + 超期标记
     * （status ∉ FINISHED,CLOSED 且 plan_end_date &lt; 今天）。
     * 区间过滤锚点 = COALESCE(plan_start_date, DATE(create_time))。
     *
     * @return 元素 {orderId, orderNo, materialCode, materialName, planQty, finishQty,
     *         status, planStartDate, planEndDate, overdue(1/0)}
     */
    List<Map<String, Object>> workOrderExecutionRows(@Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    // ==================== 报工工时统计 ====================

    /**
     * 报工按工序聚合：合格数/不良数/工时（分钟）/参与员工数。
     *
     * @param from         报工时间起（含，datetime）
     * @param to           报工时间止（不含，datetime）
     * @param workCenterId 工作中心 ID（可空 = 全部，经 wo_step 关联过滤）
     */
    List<Map<String, Object>> workhoursByOperation(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to,
                                                   @Param("workCenterId") Long workCenterId);

    /**
     * 报工按员工聚合：合格数/不良数/工时（分钟）/涉及工序数。
     */
    List<Map<String, Object>> workhoursByEmployee(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to,
                                                  @Param("workCenterId") Long workCenterId);

    // ==================== 材料损耗分析 ====================

    /**
     * 材料损耗行（工单 × 领料物料）：应耗 = 已审核领料单 should_qty（BOM 应耗）合计，
     * 实耗 = actual_qty 合计；应耗 0 的行跳过（无法计算超耗率）。
     *
     * @return 元素 {workOrderId, orderNo, materialId, materialCode, materialName, shouldQty, actualQty}
     */
    List<Map<String, Object>> materialLossRows(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    // ==================== 费用分析 ====================

    /**
     * 费用分析行（部门 × 科目）：POSTED 凭证损益/成本类借方科目发生额。
     * 部门 = 凭证制单人所属部门（fin_voucher 无部门列的口径降级）。
     *
     * @return 元素 {deptId, deptName, subjectCode, subjectName, amount}
     */
    List<Map<String, Object>> expenseRows(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ==================== 计件工资汇总 ====================

    /**
     * 计件工资行（员工 × 工序）：期间工资表明细汇总 产量 / 工资 / 工单数。
     *
     * @param period 期间 yyyy-MM（hr_piece_wage.period）
     */
    List<Map<String, Object>> piecewageRows(@Param("period") String period);
}
