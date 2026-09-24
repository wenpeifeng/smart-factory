package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.query.PurchaseDetailQuery;
import com.erp.report.dto.query.SupplierPerformanceQuery;
import com.erp.report.dto.vo.ReportRowVO;
import com.erp.report.dto.vo.SupplierPerformanceVO;
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
 * 采购明细报表接口（JSON / CSV 两种出参，&amp;export=csv 切换）。
 * <p>
 * CSV 分支直接写 Servlet 响应流后返回 null，不再走 Result 包装。
 */
@Tag(name = "报表中心-采购明细")
@RestController
@RequestMapping("/report/purchase")
@RequiredArgsConstructor
public class ReportPurchaseController {

    private final ReportQueryService reportQueryService;
    private final ReportStandardService reportStandardService;

    /**
     * 采购明细聚合：groupBy=material|supplier|month（默认 material），
     * 返回数量/含税价税合计；export=csv 时返回 CSV 附件。
     */
    @Operation(summary = "采购明细聚合（export=csv 导出）")
    @RequirePerm("report:purchase:list")
    @GetMapping("/detail")
    public Object detail(@ModelAttribute PurchaseDetailQuery query, HttpServletResponse response) throws IOException {
        List<ReportRowVO> rows = reportQueryService.purchaseDetail(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(rows);
        }
        String groupBy = query.getGroupBy() == null || query.getGroupBy().isBlank()
                ? "material" : query.getGroupBy().trim().toLowerCase();
        List<String> headers = new ArrayList<>();
        if ("month".equals(groupBy)) {
            headers.add("月份");
        } else if ("supplier".equals(groupBy)) {
            headers.add("供应商编码");
            headers.add("供应商名称");
        } else {
            headers.add("物料编码");
            headers.add("物料名称");
        }
        headers.addAll(List.of("数量", "价税合计"));

        List<List<String>> data = new ArrayList<>(rows.size());
        for (ReportRowVO row : rows) {
            List<String> line = new ArrayList<>(headers.size());
            line.add(CsvExporter.nvl(row.getDimCode()));
            if (!"month".equals(groupBy)) {
                line.add(CsvExporter.nvl(row.getDimName()));
            }
            line.add(CsvExporter.plain(row.getQty()));
            line.add(CsvExporter.plain(row.getAmount()));
            data.add(line);
        }
        CsvExporter.export(response, CsvExporter.filename("purchase_detail", groupBy), headers, data);
        return null;
    }

    /**
     * 供应商绩效：采购额/订单数/到货及时率（入库日期 ≤ 计划交货日期的明细行占比）/
     * IQC 质检合格率（无质检数据为 N/A）；export=csv 时返回 CSV 附件。
     */
    @Operation(summary = "供应商绩效分析（export=csv 导出）")
    @RequirePerm("report:supplier:list")
    @GetMapping("/supplier-performance")
    public Object supplierPerformance(@ModelAttribute SupplierPerformanceQuery query,
                                      HttpServletResponse response) throws IOException {
        SupplierPerformanceVO vo = reportStandardService.supplierPerformance(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("供应商编码", "供应商名称", "订单数", "订单金额(元)",
                "入库金额(元)", "到货明细行数", "到货及时行数", "到货及时率(%)",
                "质检抽样数", "质检合格数", "质检合格率(%)");
        List<List<String>> data = new ArrayList<>(vo.getRows().size());
        for (SupplierPerformanceVO.Row r : vo.getRows()) {
            data.add(List.of(
                    CsvExporter.nvl(r.getSupplierCode()),
                    CsvExporter.nvl(r.getSupplierName()),
                    r.getOrderCount() == null ? "" : r.getOrderCount().toString(),
                    CsvExporter.plain(r.getOrderAmount()),
                    CsvExporter.plain(r.getInstockAmount()),
                    r.getReceiptLineCount() == null ? "" : r.getReceiptLineCount().toString(),
                    r.getOnTimeLineCount() == null ? "" : r.getOnTimeLineCount().toString(),
                    r.getOnTimeRate() == null ? "N/A" : CsvExporter.plain(r.getOnTimeRate()),
                    CsvExporter.plain(r.getSampleQty()),
                    CsvExporter.plain(r.getQualifiedQty()),
                    r.getQualifiedRate() == null ? "N/A" : CsvExporter.plain(r.getQualifiedRate())));
        }
        CsvExporter.export(response, CsvExporter.filename("supplier_performance", "all"), headers, data);
        return null;
    }
}
