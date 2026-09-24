package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.query.MonthlySummaryQuery;
import com.erp.report.dto.vo.MonthlySummaryVO;
import com.erp.report.service.ReportStandardService;
import com.erp.report.support.CsvExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 经营月报接口（JSON / CSV 两种出参，&amp;export=csv 切换）。
 */
@Tag(name = "报表中心-经营月报")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportMonthlyController {

    private final ReportStandardService reportStandardService;

    /**
     * 经营月报：month=yyyy-MM（默认当月）。一页纸指标 ——
     * 完工工单数/产量/销量/销售额/采购额/毛利率/费用合计/应收应付/库存总值。
     */
    @Operation(summary = "经营月报（export=csv 导出）")
    @RequirePerm("report:monthly:list")
    @GetMapping("/monthly-summary")
    public Object monthlySummary(@ModelAttribute MonthlySummaryQuery query,
                                 HttpServletResponse response) throws IOException {
        MonthlySummaryVO vo = reportStandardService.monthlySummary(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("指标", "数值");
        List<List<String>> data = new ArrayList<>(List.of(
                row("月份", vo.getMonth()),
                row("完工工单数(张)", vo.getFinishedOrderCount()),
                row("产量(完工数量)", vo.getProductionQty()),
                row("销量(出库数量)", vo.getSalesQty()),
                row("销售额(元,不含税)", vo.getSalesAmount()),
                row("采购额(元,价税合计)", vo.getPurchaseAmount()),
                row("营业收入(元)", vo.getRevenue()),
                row("营收口径", vo.getRevenueSource()),
                row("营业成本(元)", vo.getCost()),
                row("成本口径", vo.getCostSource()),
                row("毛利(元)", vo.getGrossProfit()),
                row("毛利率(%)", vo.getGrossMargin()),
                row("费用合计(元,66xx损益科目)", vo.getExpenseTotal()),
                row("应收余额(元,时点)", vo.getReceivableBalance()),
                row("应付余额(元,时点)", vo.getPayableBalance()),
                row("库存总值(元,时点)", vo.getInventoryValue())));
        CsvExporter.export(response,
                CsvExporter.filename("monthly_summary", vo.getMonth().replace("-", "")), headers, data);
        return null;
    }

    private static List<String> row(String label, String value) {
        return List.of(label, CsvExporter.nvl(value));
    }

    private static List<String> row(String label, java.math.BigDecimal value) {
        return List.of(label, CsvExporter.plain(value));
    }

    private static List<String> row(String label, Long value) {
        return List.of(label, value == null ? "" : value.toString());
    }
}
