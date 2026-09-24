package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.query.SalesDetailQuery;
import com.erp.report.dto.query.SalesMarginQuery;
import com.erp.report.dto.vo.ReportRowVO;
import com.erp.report.dto.vo.SalesMarginVO;
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
 * 销售明细报表接口（JSON / CSV 两种出参，&amp;export=csv 切换）。
 * <p>
 * CSV 分支直接写 Servlet 响应流后返回 null（Spring 对 null 视为响应已处理，
 * 不再走 Result 包装），因此本接口返回类型为 Object。
 */
@Tag(name = "报表中心-销售明细")
@RestController
@RequestMapping("/report/sales")
@RequiredArgsConstructor
public class ReportSalesController {

    private final ReportQueryService reportQueryService;
    private final ReportStandardService reportStandardService;

    /**
     * 销售明细聚合：groupBy=material|customer|month（默认 material），
     * 返回数量/不含税额/参考成本/毛利；export=csv 时返回 CSV 附件。
     */
    @Operation(summary = "销售明细聚合（export=csv 导出）")
    @RequirePerm("report:sales:list")
    @GetMapping("/detail")
    public Object detail(@ModelAttribute SalesDetailQuery query, HttpServletResponse response) throws IOException {
        List<ReportRowVO> rows = reportQueryService.salesDetail(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(rows);
        }
        String groupBy = query.getGroupBy() == null || query.getGroupBy().isBlank()
                ? "material" : query.getGroupBy().trim().toLowerCase();
        List<String> headers = new ArrayList<>();
        if ("month".equals(groupBy)) {
            headers.add("月份");
        } else if ("customer".equals(groupBy)) {
            headers.add("客户编码");
            headers.add("客户名称");
        } else {
            headers.add("物料编码");
            headers.add("物料名称");
        }
        headers.addAll(List.of("数量", "不含税额", "参考成本", "毛利"));

        List<List<String>> data = new ArrayList<>(rows.size());
        for (ReportRowVO row : rows) {
            List<String> line = new ArrayList<>(headers.size());
            if ("month".equals(groupBy)) {
                line.add(CsvExporter.nvl(row.getDimCode()));
            } else {
                line.add(CsvExporter.nvl(row.getDimCode()));
                line.add(CsvExporter.nvl(row.getDimName()));
            }
            line.add(CsvExporter.plain(row.getQty()));
            line.add(CsvExporter.plain(row.getAmount()));
            line.add(CsvExporter.plain(row.getCostAmount()));
            line.add(CsvExporter.plain(row.getProfitAmount()));
            data.add(line);
        }
        CsvExporter.export(response, CsvExporter.filename("sales_detail", groupBy), headers, data);
        return null;
    }

    /**
     * 销售毛利分析：groupBy=customer|material（默认 material），
     * 返回数量/销售额/成本(actual_cost,历史行回退 ref_cost)/毛利/毛利率；
     * export=csv 时返回 CSV 附件（含合计行）。
     */
    @Operation(summary = "销售毛利分析（export=csv 导出）")
    @RequirePerm("report:margin:list")
    @GetMapping("/margin")
    public Object margin(@ModelAttribute SalesMarginQuery query, HttpServletResponse response) throws IOException {
        SalesMarginVO vo = reportStandardService.salesMargin(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        String groupBy = vo.getGroupBy();
        List<String> headers = new ArrayList<>();
        if ("customer".equals(groupBy)) {
            headers.add("客户编码");
            headers.add("客户名称");
        } else {
            headers.add("物料编码");
            headers.add("物料名称");
        }
        headers.addAll(List.of("数量", "销售额(元)", "销售成本(元)", "毛利(元)", "毛利率(%)"));

        List<List<String>> data = new ArrayList<>(vo.getRows().size() + 1);
        for (SalesMarginVO.Row row : vo.getRows()) {
            data.add(new ArrayList<>(List.of(
                    CsvExporter.nvl(row.getDimCode()),
                    CsvExporter.nvl(row.getDimName()),
                    CsvExporter.plain(row.getQty()),
                    CsvExporter.plain(row.getAmount()),
                    CsvExporter.plain(row.getCostAmount()),
                    CsvExporter.plain(row.getProfitAmount()),
                    CsvExporter.plain(row.getMarginPct()))));
        }
        SalesMarginVO.Total t = vo.getTotal();
        data.add(new ArrayList<>(List.of(
                "合计", "",
                CsvExporter.plain(t.getQty()),
                CsvExporter.plain(t.getAmount()),
                CsvExporter.plain(t.getCostAmount()),
                CsvExporter.plain(t.getProfitAmount()),
                CsvExporter.plain(t.getMarginPct()))));
        CsvExporter.export(response, CsvExporter.filename("sales_margin", groupBy), headers, data);
        return null;
    }
}
