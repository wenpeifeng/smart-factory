package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.query.PiecewageQuery;
import com.erp.report.dto.vo.PiecewageVO;
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
 * 人事报表接口（计件工资汇总，JSON / CSV 两种出参，&amp;export=csv 切换）。
 */
@Tag(name = "报表中心-人事报表")
@RestController
@RequestMapping("/report/hr")
@RequiredArgsConstructor
public class ReportHrController {

    private final ReportStandardService reportStandardService;

    /**
     * 计件工资汇总：period=yyyy-MM（默认当月）。
     * 员工 × 工序的计件产量 / 工资 / 工单数；期间无工资表数据返回空列表。
     */
    @Operation(summary = "计件工资汇总（export=csv 导出）")
    @RequirePerm("report:hr:list")
    @GetMapping("/piecewage")
    public Object piecewage(@ModelAttribute PiecewageQuery query,
                            HttpServletResponse response) throws IOException {
        PiecewageVO vo = reportStandardService.piecewage(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("工号", "员工", "工序", "计件产量", "计件工资(元)", "工单数");
        List<List<String>> data = new ArrayList<>(vo.getRows().size() + 1);
        for (PiecewageVO.Row r : vo.getRows()) {
            data.add(List.of(
                    CsvExporter.nvl(r.getEmpNo()),
                    CsvExporter.nvl(r.getEmployeeName()),
                    CsvExporter.nvl(r.getProcessName()),
                    CsvExporter.plain(r.getQty()),
                    CsvExporter.plain(r.getAmount()),
                    r.getWoCount() == null ? "" : r.getWoCount().toString()));
        }
        PiecewageVO.Total t = vo.getTotal();
        data.add(List.of("合计", "", "",
                CsvExporter.plain(t.getQty()), CsvExporter.plain(t.getAmount()), ""));
        CsvExporter.export(response, CsvExporter.filename("piecewage",
                vo.getPeriod().replace("-", "")), headers, data);
        return null;
    }
}
