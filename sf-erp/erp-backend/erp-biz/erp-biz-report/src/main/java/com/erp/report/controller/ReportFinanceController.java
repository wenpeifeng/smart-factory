package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.query.ExpenseQuery;
import com.erp.report.dto.query.FinanceSummaryQuery;
import com.erp.report.dto.vo.ExpenseVO;
import com.erp.report.dto.vo.FinanceSummaryVO;
import com.erp.report.service.ReportQueryService;
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
 * 财务汇总报表接口（JSON / CSV 两种出参，&amp;export=csv 切换）。
 */
@Tag(name = "报表中心-财务汇总")
@RestController
@RequestMapping("/report/finance")
@RequiredArgsConstructor
public class ReportFinanceController {

    private final ReportQueryService reportQueryService;
    private final ReportStandardService reportStandardService;

    /**
     * 财务汇总：period=yyyy-MM（默认当月）。
     * 营收/成本优先取已过账凭证的 6001/6401 科目汇总，无凭证时降级业务口径；
     * 另含应收/应付余额（时点）与现金流净额。
     */
    @Operation(summary = "财务汇总（export=csv 导出）")
    @RequirePerm("report:finance:list")
    @GetMapping("/summary")
    public Object summary(@ModelAttribute FinanceSummaryQuery query, HttpServletResponse response) throws IOException {
        FinanceSummaryVO vo = reportQueryService.financeSummary(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("指标", "数值");
        List<List<String>> data = new ArrayList<>(List.of(
                row("期间", vo.getPeriod()),
                row("营业收入(元)", vo.getRevenue()),
                row("营收口径", vo.getRevenueSource()),
                row("营业成本(元)", vo.getCost()),
                row("成本口径", vo.getCostSource()),
                row("毛利(元)", vo.getGrossProfit()),
                row("毛利率(%)", vo.getGrossMargin()),
                row("应收余额(元)", vo.getReceivableBalance()),
                row("应付余额(元)", vo.getPayableBalance()),
                row("现金流入(元)", vo.getCashIn()),
                row("现金流出(元)", vo.getCashOut()),
                row("现金流净额(元)", vo.getCashNet())));
        CsvExporter.export(response, CsvExporter.filename("finance_summary", vo.getPeriod().replace("-", "")),
                headers, data);
        return null;
    }

    /**
     * 费用分析：period=yyyy-MM（默认当月）。
     * 部门 × 科目 借方发生额（POSTED 凭证，损益+成本类借方科目；
     * 部门 = 凭证制单人所属部门，无部门归「未分配部门」）。
     */
    @Operation(summary = "费用分析（export=csv 导出）")
    @RequirePerm("report:finance:list")
    @GetMapping("/expense")
    public Object expense(@ModelAttribute ExpenseQuery query,
                          HttpServletResponse response) throws IOException {
        ExpenseVO vo = reportStandardService.expense(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("部门", "科目编码", "科目名称", "借方发生额(元)", "占比");
        List<List<String>> data = new ArrayList<>(vo.getRows().size() + 1);
        for (ExpenseVO.Row r : vo.getRows()) {
            data.add(List.of(
                    CsvExporter.nvl(r.getDeptName()),
                    CsvExporter.nvl(r.getSubjectCode()),
                    CsvExporter.nvl(r.getSubjectName()),
                    CsvExporter.plain(r.getAmount()),
                    CsvExporter.plain(r.getRatio())));
        }
        data.add(List.of("合计", "", "",
                CsvExporter.plain(vo.getTotalAmount()), ""));
        CsvExporter.export(response, CsvExporter.filename("finance_expense",
                vo.getPeriod().replace("-", "")), headers, data);
        return null;
    }

    private static List<String> row(String label, String value) {
        return List.of(label, CsvExporter.nvl(value));
    }

    private static List<String> row(String label, java.math.BigDecimal value) {
        return List.of(label, CsvExporter.plain(value));
    }
}
