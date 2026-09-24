package com.erp.report.service.impl;

import com.erp.report.dto.vo.DashboardVO;
import com.erp.report.mapper.ReportMapper;
import com.erp.report.service.ReportDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱聚合服务实现。
 * <p>
 * 「本期」= 当前自然月（[月初, 次月初) 半开区间），环比 = 上一个自然月整月；
 * 库存总值 / 应收 / 应付为当前时点快照。wf_task 为工作流模块约定表，
 * 未建表/查询异常时待办数降级为 0，不影响驾驶舱整体可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportDashboardServiceImpl implements ReportDashboardService {

    /** 走势月份数 */
    private static final int TREND_MONTHS = 6;
    /** 当月销售 TOP 物料数 */
    private static final int TOP_MATERIALS = 5;
    /** 工作流待办约定表名（erp-workflow 模块） */
    private static final String WF_TASK_TABLE = "wf_task";

    private final ReportMapper reportMapper;

    @Override
    public DashboardVO dashboard() {
        LocalDate today = LocalDate.now();
        YearMonth cur = YearMonth.from(today);
        YearMonth prev = cur.minusMonths(1);
        LocalDate curStart = cur.atDay(1);
        LocalDate curEnd = cur.plusMonths(1).atDay(1);
        LocalDate prevStart = prev.atDay(1);

        DashboardVO vo = new DashboardVO();
        vo.setMonth(cur.toString());
        vo.setPrevMonth(prev.toString());

        // ---- 本期 / 环比基础指标 ----
        vo.setSalesAmount(reportMapper.sumSalesAmount(curStart, curEnd));
        vo.setSalesAmountPrev(reportMapper.sumSalesAmount(prevStart, curStart));
        vo.setPurchaseAmount(reportMapper.sumPurchaseAmount(curStart, curEnd));
        vo.setPurchaseAmountPrev(reportMapper.sumPurchaseAmount(prevStart, curStart));
        vo.setReceivableBalance(reportMapper.sumReceivableBalance());
        vo.setPayableBalance(reportMapper.sumPayableBalance());
        vo.setPendingApprovalCount(countPendingApprovalsSafely());
        vo.setStockWarningCount(nvl(reportMapper.countStockWarnings()));
        vo.setProductionFinishQty(reportMapper.sumWorkOrderFinishQty(
                curStart.atStartOfDay(), curEnd.atStartOfDay()));
        vo.setProductionFinishQtyPrev(reportMapper.sumWorkOrderFinishQty(
                prevStart.atStartOfDay(), curStart.atStartOfDay()));

        // ---- 近 6 个月走势（无单据月份补零，保证横轴连续） ----
        LocalDate trendFrom = cur.minusMonths(TREND_MONTHS - 1L).atDay(1);
        Map<String, BigDecimal> salesByMonth = toAmountByMonth(reportMapper.monthlySalesTrend(trendFrom));
        Map<String, BigDecimal> purchaseByMonth = toAmountByMonth(reportMapper.monthlyPurchaseTrend(trendFrom));
        List<DashboardVO.MonthlyTrendVO> trends = new ArrayList<>(TREND_MONTHS);
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            String ym = cur.minusMonths(i).toString();
            DashboardVO.MonthlyTrendVO t = new DashboardVO.MonthlyTrendVO();
            t.setMonth(ym);
            t.setSalesAmount(salesByMonth.getOrDefault(ym, BigDecimal.ZERO));
            t.setPurchaseAmount(purchaseByMonth.getOrDefault(ym, BigDecimal.ZERO));
            trends.add(t);
        }
        vo.setTrends(trends);

        // ---- 库存总值 + 仓库价值分布（同一份明细两种聚合） ----
        List<Map<String, Object>> invRows = reportMapper.inventoryValueRows();
        Map<Long, DashboardVO.WarehouseValueVO> byWarehouse = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Map<String, Object> row : invRows) {
            Long warehouseId = asLong(row.get("warehouseId"));
            DashboardVO.WarehouseValueVO wv = byWarehouse.computeIfAbsent(warehouseId, id -> {
                DashboardVO.WarehouseValueVO n = new DashboardVO.WarehouseValueVO();
                n.setWarehouseId(id);
                n.setWarehouseName(asString(row.get("warehouseName")));
                n.setValue(BigDecimal.ZERO);
                return n;
            });
            BigDecimal value = asDecimal(row.get("value"));
            wv.setValue(wv.getValue().add(value));
            totalValue = totalValue.add(value);
        }
        vo.setInventoryValue(totalValue);
        vo.setWarehouseValues(new ArrayList<>(byWarehouse.values()));

        // ---- 当月销售 TOP5 物料 ----
        List<DashboardVO.TopMaterialVO> tops = new ArrayList<>();
        for (Map<String, Object> row : reportMapper.salesTopMaterials(curStart, curEnd, TOP_MATERIALS)) {
            DashboardVO.TopMaterialVO tm = new DashboardVO.TopMaterialVO();
            tm.setMaterialId(asLong(row.get("materialId")));
            tm.setMaterialCode(asString(row.get("materialCode")));
            tm.setMaterialName(asString(row.get("materialName")));
            tm.setAmount(asDecimal(row.get("amount")));
            tm.setQty(asDecimal(row.get("qty")));
            tops.add(tm);
        }
        vo.setTopMaterials(tops);
        return vo;
    }

    /**
     * 待办审批数：工作流模块约定表未上线（或查询异常）时降级为 0。
     * 先查 information_schema 确认表存在，避免直接 SQL 报错打断整个驾驶舱。
     */
    private Long countPendingApprovalsSafely() {
        try {
            if (reportMapper.countTableExists(WF_TASK_TABLE) == 0) {
                return 0L;
            }
            return nvl(reportMapper.countPendingTasks());
        } catch (Exception e) {
            log.warn("待办审批数统计降级为 0：{}", e.getMessage());
            return 0L;
        }
    }

    /** 月度走势行 → {yyyy-MM: amount} */
    private Map<String, BigDecimal> toAmountByMonth(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(asString(row.get("month")), asDecimal(row.get("amount")));
        }
        return result;
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

    private static BigDecimal asDecimal(Object value) {
        return value instanceof BigDecimal d ? d
                : value instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
                : BigDecimal.ZERO;
    }
}
