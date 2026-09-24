package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.query.ExecutionQuery;
import com.erp.report.dto.query.MaterialLossQuery;
import com.erp.report.dto.query.WorkhoursQuery;
import com.erp.report.dto.vo.MaterialLossVO;
import com.erp.report.dto.vo.WorkExecutionVO;
import com.erp.report.dto.vo.WorkhoursVO;
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
 * 生产报表接口（订单执行跟踪 / 报工工时统计 / 材料损耗分析，
 * JSON / CSV 两种出参，&amp;export=csv 切换）。
 */
@Tag(name = "报表中心-生产报表")
@RestController
@RequestMapping("/report/production")
@RequiredArgsConstructor
public class ReportProductionController {

    private final ReportStandardService reportStandardService;

    /**
     * 订单执行跟踪：工单 计划量/完工量/在制/完工率/超期标记
     * （超期 = 未完工未关闭且计划完工日期 &lt; 今天）。
     */
    @Operation(summary = "生产订单执行跟踪（export=csv 导出）")
    @RequirePerm("report:production:list")
    @GetMapping("/execution")
    public Object execution(@ModelAttribute ExecutionQuery query,
                            HttpServletResponse response) throws IOException {
        WorkExecutionVO vo = reportStandardService.workOrderExecution(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("工单号", "物料编码", "物料名称", "计划量", "完工量",
                "在制量", "完工率(%)", "状态", "计划开工", "计划完工", "是否超期");
        List<List<String>> data = new ArrayList<>(vo.getRows().size());
        for (WorkExecutionVO.Row r : vo.getRows()) {
            data.add(List.of(
                    CsvExporter.nvl(r.getOrderNo()),
                    CsvExporter.nvl(r.getMaterialCode()),
                    CsvExporter.nvl(r.getMaterialName()),
                    CsvExporter.plain(r.getPlanQty()),
                    CsvExporter.plain(r.getFinishQty()),
                    CsvExporter.plain(r.getWipQty()),
                    CsvExporter.plain(r.getFinishRate()),
                    CsvExporter.nvl(r.getStatusLabel()),
                    CsvExporter.nvl(r.getPlanStartDate()),
                    CsvExporter.nvl(r.getPlanEndDate()),
                    Boolean.TRUE.equals(r.getOverdue()) ? "是" : "否"));
        }
        CsvExporter.export(response, CsvExporter.filename("wo_execution", "all"), headers, data);
        return null;
    }

    /**
     * 报工工时统计：groupBy=operation|employee（默认 operation），workCenterId 可选；
     * 合格数/不良数/不良率/工时（分钟与小时）。
     */
    @Operation(summary = "报工工时统计（export=csv 导出）")
    @RequirePerm("report:production:list")
    @GetMapping("/workhours")
    public Object workhours(@ModelAttribute WorkhoursQuery query,
                            HttpServletResponse response) throws IOException {
        WorkhoursVO vo = reportStandardService.workhours(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        String groupBy = vo.getGroupBy();
        List<String> headers = new ArrayList<>();
        if ("employee".equals(groupBy)) {
            headers.add("员工");
            headers.add("涉及工序数");
        } else {
            headers.add("工序编码");
            headers.add("工序名称");
            headers.add("报工员工数");
        }
        headers.addAll(List.of("合格数", "不良数", "不良率(%)", "工时(分钟)", "工时(小时)"));

        List<List<String>> data = new ArrayList<>(vo.getRows().size());
        for (WorkhoursVO.Row r : vo.getRows()) {
            data.add(new ArrayList<>(List.of(
                    CsvExporter.nvl(r.getDimCode()),
                    CsvExporter.nvl(r.getDimName()),
                    r.getExtraCount() == null ? "" : r.getExtraCount().toString(),
                    CsvExporter.plain(r.getGoodQty()),
                    CsvExporter.plain(r.getBadQty()),
                    CsvExporter.plain(r.getBadRate()),
                    r.getWorkMinutes() == null ? "" : r.getWorkMinutes().toString(),
                    CsvExporter.plain(r.getWorkHours()))));
        }
        CsvExporter.export(response, CsvExporter.filename("workhours", groupBy), headers, data);
        return null;
    }

    /**
     * 材料损耗分析：工单 × 领料物料的 BOM 应耗 vs 实际领料（超耗量/超耗率）。
     */
    @Operation(summary = "材料损耗分析（export=csv 导出）")
    @RequirePerm("report:production:list")
    @GetMapping("/material-loss")
    public Object materialLoss(@ModelAttribute MaterialLossQuery query,
                               HttpServletResponse response) throws IOException {
        MaterialLossVO vo = reportStandardService.materialLoss(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("工单号", "物料编码", "物料名称",
                "BOM应耗", "实际领料", "超耗量", "超耗率(%)");
        List<List<String>> data = new ArrayList<>(vo.getRows().size() + 1);
        for (MaterialLossVO.Row r : vo.getRows()) {
            data.add(List.of(
                    CsvExporter.nvl(r.getOrderNo()),
                    CsvExporter.nvl(r.getMaterialCode()),
                    CsvExporter.nvl(r.getMaterialName()),
                    CsvExporter.plain(r.getShouldQty()),
                    CsvExporter.plain(r.getActualQty()),
                    CsvExporter.plain(r.getLossQty()),
                    CsvExporter.plain(r.getLossRate())));
        }
        MaterialLossVO.Total t = vo.getTotal();
        data.add(List.of("合计", "", "",
                CsvExporter.plain(t.getShouldQty()),
                CsvExporter.plain(t.getActualQty()),
                CsvExporter.plain(t.getLossQty()),
                CsvExporter.plain(t.getLossRate())));
        CsvExporter.export(response, CsvExporter.filename("material_loss", "all"), headers, data);
        return null;
    }
}
