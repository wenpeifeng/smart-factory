package com.erp.report.service.impl;

import com.erp.common.exception.BusinessException;
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
import com.erp.report.mapper.ReportMapper;
import com.erp.report.service.ReportStandardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表中心标准化·制造业报表服务实现（10 张，跨模块只读聚合）。
 * <p>
 * 统一口径：只统计已审核单据（sal_outstock/pur_instock status=10、pur_order
 * status IN (10,20,30)、领料单 AUDITED、凭证 POSTED）；日期半开区间 [from, to)；
 * SUM 全 COALESCE 兜底；比率 2 位 HALF_UP，分母为 0 时取 0 或 null（N/A）。
 */
@Service
@RequiredArgsConstructor
public class ReportStandardServiceImpl implements ReportStandardService {

    /** 期间费用科目编码前缀（6601 销售 / 6602 管理 / 6603 财务…），经营月报费用合计口径 */
    private static final String EXPENSE_SUBJECT_PREFIX = "66";

    /** 工单状态 → 中文 */
    private static final Map<String, String> WO_STATUS_LABEL = Map.of(
            "DRAFT", "草稿", "RELEASED", "已下达", "PRODUCING", "生产中",
            "FINISHED", "已完工", "CLOSED", "已关闭");

    private final ReportMapper reportMapper;

    // ==================== 1. 经营月报 ====================

    @Override
    public MonthlySummaryVO monthlySummary(MonthlySummaryQuery query) {
        YearMonth month = parsePeriod(query.getMonth(), "month");
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);

        MonthlySummaryVO vo = new MonthlySummaryVO();
        vo.setMonth(month.toString());
        vo.setFinishedOrderCount(nvl(reportMapper.countFinishedWorkOrders(
                from.atStartOfDay(), to.atStartOfDay())));
        vo.setProductionQty(reportMapper.sumWorkOrderFinishQty(from.atStartOfDay(), to.atStartOfDay()));
        vo.setSalesQty(reportMapper.sumSalesQty(from, to));
        vo.setSalesAmount(reportMapper.sumSalesAmount(from, to));
        vo.setPurchaseAmount(reportMapper.sumPurchaseAmount(from, to));

        // 营收/成本/毛利：与财务汇总完全同口径（凭证优先，业务降级）
        boolean hasVoucher = reportMapper.countPostedVouchers(from, to) > 0;
        if (hasVoucher) {
            vo.setRevenue(reportMapper.sumVoucherSubjectAmount("CREDIT", "6001", from, to));
            vo.setCost(reportMapper.sumVoucherSubjectAmount("DEBIT", "6401", from, to));
            vo.setRevenueSource("VOUCHER");
            vo.setCostSource("VOUCHER");
        } else {
            vo.setRevenue(vo.getSalesAmount());
            vo.setCost(reportMapper.sumSalesRefCost(from, to));
            vo.setRevenueSource("BIZ");
            vo.setCostSource("BIZ");
        }
        vo.setGrossProfit(vo.getRevenue().subtract(vo.getCost()));
        vo.setGrossMargin(pct(vo.getGrossProfit(), vo.getRevenue()));

        // 费用合计：POSTED 凭证 66 开头损益科目借方发生额（期间费用）
        vo.setExpenseTotal(hasVoucher
                ? reportMapper.sumVoucherSubjectAmount("DEBIT", EXPENSE_SUBJECT_PREFIX, from, to)
                : BigDecimal.ZERO);

        // 应收/应付/库存总值：当前时点快照（fin_receivable / inv_stock 无期间余额表）
        vo.setReceivableBalance(reportMapper.sumReceivableBalance());
        vo.setPayableBalance(reportMapper.sumPayableBalance());
        BigDecimal inventoryValue = BigDecimal.ZERO;
        for (Map<String, Object> row : reportMapper.inventoryValueRows()) {
            inventoryValue = inventoryValue.add(asDecimal(row.get("value")));
        }
        vo.setInventoryValue(inventoryValue);
        return vo;
    }

    // ==================== 2. 销售毛利分析 ====================

    @Override
    public SalesMarginVO salesMargin(SalesMarginQuery query) {
        String groupBy = normalizeGroup(query.getGroupBy(), Set.of("material", "customer"), "material");
        List<Map<String, Object>> rows = "customer".equals(groupBy)
                ? reportMapper.salesMarginByCustomer(query.getFrom(), query.getTo())
                : reportMapper.salesMarginByMaterial(query.getFrom(), query.getTo());

        SalesMarginVO vo = new SalesMarginVO();
        vo.setGroupBy(groupBy);
        List<SalesMarginVO.Row> list = new ArrayList<>(rows.size());
        SalesMarginVO.Total total = new SalesMarginVO.Total();
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            SalesMarginVO.Row r = new SalesMarginVO.Row();
            r.setDimCode(asString(row.get("dimCode")));
            r.setDimName(asString(row.get("dimName")));
            r.setQty(asDecimal(row.get("qty")));
            r.setAmount(asDecimal(row.get("amount")));
            r.setCostAmount(asDecimal(row.get("costAmount")));
            r.setProfitAmount(asDecimal(row.get("profitAmount")));
            r.setMarginPct(pct(r.getProfitAmount(), r.getAmount()));
            list.add(r);
            qty = qty.add(r.getQty());
            amount = amount.add(r.getAmount());
            cost = cost.add(r.getCostAmount());
        }
        vo.setRows(list);
        total.setQty(qty);
        total.setAmount(amount);
        total.setCostAmount(cost);
        total.setProfitAmount(amount.subtract(cost));
        total.setMarginPct(pct(total.getProfitAmount(), amount));
        vo.setTotal(total);
        return vo;
    }

    // ==================== 3. 供应商绩效 ====================

    @Override
    public SupplierPerformanceVO supplierPerformance(SupplierPerformanceQuery query) {
        List<Map<String, Object>> rows = reportMapper.supplierPerformanceRows(query.getFrom(), query.getTo());
        List<SupplierPerformanceVO.Row> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            SupplierPerformanceVO.Row r = new SupplierPerformanceVO.Row();
            r.setSupplierId(asLong(row.get("supplierId")));
            r.setSupplierCode(asString(row.get("supplierCode")));
            r.setSupplierName(asString(row.get("supplierName")));
            r.setOrderCount(nvl(asLong(row.get("orderCount"))));
            r.setOrderAmount(asDecimal(row.get("orderAmount")));
            r.setInstockAmount(asDecimal(row.get("instockAmount")));
            r.setReceiptLineCount(asLong(row.get("receiptLineCount")));
            r.setOnTimeLineCount(asLong(row.get("onTimeLineCount")));
            r.setSampleQty(asNullableDecimal(row.get("sampleQty")));
            r.setQualifiedQty(asNullableDecimal(row.get("qualifiedQty")));
            // 及时率/合格率：分母缺失（无到货行/无质检数据）→ null（前端 N/A）
            r.setOnTimeRate(pctNullable(r.getOnTimeLineCount(), r.getReceiptLineCount()));
            r.setQualifiedRate(pctNullable(r.getQualifiedQty(), r.getSampleQty()));
            list.add(r);
        }
        SupplierPerformanceVO vo = new SupplierPerformanceVO();
        vo.setRows(list);
        return vo;
    }

    // ==================== 4. 收发存月报 ====================

    @Override
    public InventoryMonthlyVO inventoryMonthly(InventoryMonthlyQuery query) {
        YearMonth month = parsePeriod(query.getMonth(), "month");
        List<Map<String, Object>> rows = reportMapper.inventoryMonthlyRows(month.toString().replace("-", ""));

        InventoryMonthlyVO vo = new InventoryMonthlyVO();
        vo.setMonth(month.toString());
        List<InventoryMonthlyVO.Row> list = new ArrayList<>(rows.size());
        InventoryMonthlyVO.Total total = new InventoryMonthlyVO.Total();
        BigDecimal[] sums = new BigDecimal[6];
        java.util.Arrays.fill(sums, BigDecimal.ZERO);
        for (Map<String, Object> row : rows) {
            InventoryMonthlyVO.Row r = new InventoryMonthlyVO.Row();
            r.setMaterialId(asLong(row.get("materialId")));
            r.setMaterialCode(asString(row.get("materialCode")));
            r.setMaterialName(asString(row.get("materialName")));
            r.setUnitName(asString(row.get("unitName")));
            r.setBeginQty(asDecimal(row.get("beginQty")));
            r.setBeginAmount(asDecimal(row.get("beginAmount")));
            r.setInQty(asDecimal(row.get("inQty")));
            r.setInAmount(asDecimal(row.get("inAmount")));
            r.setOutQty(asDecimal(row.get("outQty")));
            r.setOutAmount(asDecimal(row.get("outAmount")));
            // 期末 = 期初 + 入 − 出（守恒关系由此构造保证）
            r.setEndQty(r.getBeginQty().add(r.getInQty()).subtract(r.getOutQty()));
            r.setEndAmount(r.getBeginAmount().add(r.getInAmount()).subtract(r.getOutAmount()));
            list.add(r);
            sums[0] = sums[0].add(r.getBeginQty());
            sums[1] = sums[1].add(r.getBeginAmount());
            sums[2] = sums[2].add(r.getInQty());
            sums[3] = sums[3].add(r.getInAmount());
            sums[4] = sums[4].add(r.getOutQty());
            sums[5] = sums[5].add(r.getOutAmount());
        }
        vo.setRows(list);
        total.setBeginQty(sums[0]);
        total.setBeginAmount(sums[1]);
        total.setInQty(sums[2]);
        total.setInAmount(sums[3]);
        total.setOutQty(sums[4]);
        total.setOutAmount(sums[5]);
        total.setEndQty(sums[0].add(sums[2]).subtract(sums[4]));
        total.setEndAmount(sums[1].add(sums[3]).subtract(sums[5]));
        vo.setTotal(total);
        return vo;
    }

    // ==================== 5. 呆滞物料 ====================

    @Override
    public SluggishVO sluggishMaterials(SluggishQuery query) {
        int days = query.getDays() == null ? 90 : query.getDays();
        if (days <= 0) {
            throw BusinessException.ofKey("erp.report.stale-days-positive");
        }
        List<Map<String, Object>> rows = reportMapper.sluggishMaterialRows(days);

        SluggishVO vo = new SluggishVO();
        vo.setDays(days);
        BigDecimal totalValue = BigDecimal.ZERO;
        List<SluggishVO.Row> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            SluggishVO.Row r = new SluggishVO.Row();
            r.setMaterialId(asLong(row.get("materialId")));
            r.setMaterialCode(asString(row.get("materialCode")));
            r.setMaterialName(asString(row.get("materialName")));
            r.setUnitName(asString(row.get("unitName")));
            r.setQty(asDecimal(row.get("qty")));
            r.setPrice(asDecimal(row.get("price")));
            r.setValue(asDecimal(row.get("value")));
            r.setLastOutTime(asString(row.get("lastOutTime")));
            r.setDaysSinceOut(asInteger(row.get("daysSinceOut")));
            r.setLastInTime(asString(row.get("lastInTime")));
            r.setAgeDays(asInteger(row.get("ageDays")));
            list.add(r);
            totalValue = totalValue.add(r.getValue());
        }
        final BigDecimal total = totalValue;
        for (SluggishVO.Row r : list) {
            r.setRatio(total.signum() == 0 ? BigDecimal.ZERO
                    : r.getValue().divide(total, 4, RoundingMode.HALF_UP));
        }
        vo.setTotalValue(totalValue);
        vo.setRows(list);
        return vo;
    }

    // ==================== 6. 订单执行跟踪 ====================

    @Override
    public WorkExecutionVO workOrderExecution(ExecutionQuery query) {
        List<Map<String, Object>> rows = reportMapper.workOrderExecutionRows(query.getFrom(), query.getTo());
        List<WorkExecutionVO.Row> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            WorkExecutionVO.Row r = new WorkExecutionVO.Row();
            r.setOrderId(asLong(row.get("orderId")));
            r.setOrderNo(asString(row.get("orderNo")));
            r.setMaterialCode(asString(row.get("materialCode")));
            r.setMaterialName(asString(row.get("materialName")));
            r.setPlanQty(asDecimal(row.get("planQty")));
            r.setFinishQty(asDecimal(row.get("finishQty")));
            BigDecimal wip = r.getPlanQty().subtract(r.getFinishQty());
            r.setWipQty(wip.max(BigDecimal.ZERO));
            r.setFinishRate(pct(r.getFinishQty(), r.getPlanQty()));
            String status = asString(row.get("status"));
            r.setStatus(status);
            r.setStatusLabel(WO_STATUS_LABEL.getOrDefault(status, status));
            r.setPlanStartDate(asString(row.get("planStartDate")));
            r.setPlanEndDate(asString(row.get("planEndDate")));
            r.setOverdue(asInteger(row.get("overdue")) != null && asInteger(row.get("overdue")) == 1);
            list.add(r);
        }
        WorkExecutionVO vo = new WorkExecutionVO();
        vo.setRows(list);
        return vo;
    }

    // ==================== 7. 报工工时统计 ====================

    @Override
    public WorkhoursVO workhours(WorkhoursQuery query) {
        String groupBy = normalizeGroup(query.getGroupBy(), Set.of("operation", "employee"), "operation");
        LocalDateTime from = query.getFrom() == null ? null : query.getFrom().atStartOfDay();
        LocalDateTime to = query.getTo() == null ? null : query.getTo().atStartOfDay();
        List<Map<String, Object>> rows = "employee".equals(groupBy)
                ? reportMapper.workhoursByEmployee(from, to, query.getWorkCenterId())
                : reportMapper.workhoursByOperation(from, to, query.getWorkCenterId());

        WorkhoursVO vo = new WorkhoursVO();
        vo.setGroupBy(groupBy);
        List<WorkhoursVO.Row> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            WorkhoursVO.Row r = new WorkhoursVO.Row();
            r.setDimCode(asString(row.get("dimCode")));
            r.setDimName(asString(row.get("dimName")));
            r.setExtraCount(nvl(asLong(row.get("employeeCount"))) + nvl(asLong(row.get("operationCount"))));
            r.setGoodQty(asDecimal(row.get("goodQty")));
            r.setBadQty(asDecimal(row.get("badQty")));
            r.setBadRate(pct(r.getBadQty(), r.getGoodQty().add(r.getBadQty())));
            long minutes = nvl(asLong(row.get("workMinutes")));
            r.setWorkMinutes(minutes);
            r.setWorkHours(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
            list.add(r);
        }
        vo.setRows(list);
        return vo;
    }

    // ==================== 8. 材料损耗分析 ====================

    @Override
    public MaterialLossVO materialLoss(MaterialLossQuery query) {
        LocalDateTime from = query.getFrom() == null ? null : query.getFrom().atStartOfDay();
        LocalDateTime to = query.getTo() == null ? null : query.getTo().atStartOfDay();
        List<Map<String, Object>> rows = reportMapper.materialLossRows(from, to);

        MaterialLossVO vo = new MaterialLossVO();
        List<MaterialLossVO.Row> list = new ArrayList<>(rows.size());
        BigDecimal should = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            MaterialLossVO.Row r = new MaterialLossVO.Row();
            r.setWorkOrderId(asLong(row.get("workOrderId")));
            r.setOrderNo(asString(row.get("orderNo")));
            r.setMaterialId(asLong(row.get("materialId")));
            r.setMaterialCode(asString(row.get("materialCode")));
            r.setMaterialName(asString(row.get("materialName")));
            r.setShouldQty(asDecimal(row.get("shouldQty")));
            r.setActualQty(asDecimal(row.get("actualQty")));
            r.setLossQty(r.getActualQty().subtract(r.getShouldQty()));
            r.setLossRate(pct(r.getLossQty(), r.getShouldQty()));
            list.add(r);
            should = should.add(r.getShouldQty());
            actual = actual.add(r.getActualQty());
        }
        vo.setRows(list);
        MaterialLossVO.Total total = new MaterialLossVO.Total();
        total.setShouldQty(should);
        total.setActualQty(actual);
        total.setLossQty(actual.subtract(should));
        total.setLossRate(pct(total.getLossQty(), should));
        vo.setTotal(total);
        return vo;
    }

    // ==================== 9. 费用分析 ====================

    @Override
    public ExpenseVO expense(ExpenseQuery query) {
        YearMonth period = parsePeriod(query.getPeriod(), "period");
        LocalDate from = period.atDay(1);
        LocalDate to = period.plusMonths(1).atDay(1);
        List<Map<String, Object>> rows = reportMapper.expenseRows(from, to);

        ExpenseVO vo = new ExpenseVO();
        vo.setPeriod(period.toString());
        BigDecimal total = BigDecimal.ZERO;
        List<ExpenseVO.Row> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ExpenseVO.Row r = new ExpenseVO.Row();
            r.setDeptId(asLong(row.get("deptId")));
            r.setDeptName(asString(row.get("deptName")));
            r.setSubjectCode(asString(row.get("subjectCode")));
            r.setSubjectName(asString(row.get("subjectName")));
            r.setAmount(asDecimal(row.get("amount")));
            list.add(r);
            total = total.add(r.getAmount());
        }
        final BigDecimal totalAmount = total;
        for (ExpenseVO.Row r : list) {
            r.setRatio(totalAmount.signum() == 0 ? BigDecimal.ZERO
                    : r.getAmount().divide(totalAmount, 4, RoundingMode.HALF_UP));
        }
        vo.setTotalAmount(totalAmount);
        vo.setRows(list);
        return vo;
    }

    // ==================== 10. 计件工资汇总 ====================

    @Override
    public PiecewageVO piecewage(PiecewageQuery query) {
        YearMonth period = parsePeriod(query.getPeriod(), "period");
        List<Map<String, Object>> rows = reportMapper.piecewageRows(period.toString());

        PiecewageVO vo = new PiecewageVO();
        vo.setPeriod(period.toString());
        List<PiecewageVO.Row> list = new ArrayList<>(rows.size());
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            PiecewageVO.Row r = new PiecewageVO.Row();
            r.setEmployeeId(asLong(row.get("employeeId")));
            r.setEmpNo(asString(row.get("empNo")));
            r.setEmployeeName(asString(row.get("employeeName")));
            r.setProcessName(asString(row.get("processName")));
            r.setQty(asDecimal(row.get("qty")));
            r.setAmount(asDecimal(row.get("amount")));
            r.setWoCount(nvl(asLong(row.get("woCount"))));
            list.add(r);
            qty = qty.add(r.getQty());
            amount = amount.add(r.getAmount());
        }
        vo.setRows(list);
        PiecewageVO.Total total = new PiecewageVO.Total();
        total.setQty(qty);
        total.setAmount(amount);
        vo.setTotal(total);
        return vo;
    }

    // ==================== 私有工具 ====================

    /** groupBy 归一化：空 → 默认值；非法值 → 业务异常 */
    private String normalizeGroup(String groupBy, Set<String> allowed, String defaultVal) {
        if (groupBy == null || groupBy.isBlank()) {
            return defaultVal;
        }
        String value = groupBy.trim().toLowerCase();
        if (!allowed.contains(value)) {
            throw BusinessException.ofKey("erp.report.groupby-unsupported", String.join("/", allowed));
        }
        return value;
    }

    /** 期间解析：空 = 当前月；格式非法 → 业务异常（字段名进错误信息） */
    private YearMonth parsePeriod(String period, String field) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey("erp.report.period-format-ym", period);
        }
    }

    /** 百分比 = part/whole×100（2 位 HALF_UP；分母空或 0 → 0） */
    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(whole, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 百分比（分母空或 0 → null = N/A，用于及时率/合格率） */
    private static BigDecimal pctNullable(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) {
            return null;
        }
        return part.divide(whole, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 百分比（Long 版，分母空或 0 → null = N/A，用于到货及时率） */
    private static BigDecimal pctNullable(Long part, Long whole) {
        if (part == null || whole == null || whole == 0) {
            return null;
        }
        return pctNullable(BigDecimal.valueOf(part), BigDecimal.valueOf(whole));
    }

    private static Long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static BigDecimal asDecimal(Object value) {
        BigDecimal d = asNullableDecimal(value);
        return d == null ? BigDecimal.ZERO : d;
    }

    private static BigDecimal asNullableDecimal(Object value) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }
}
