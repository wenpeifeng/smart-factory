package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.query.InventoryMonthlyQuery;
import com.erp.report.dto.query.SluggishQuery;
import com.erp.report.dto.vo.InventoryAgingVO;
import com.erp.report.dto.vo.InventoryMonthlyVO;
import com.erp.report.dto.vo.SluggishVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 库存库龄分析接口（JSON / CSV 两种出参，&amp;export=csv 切换）。
 */
@Tag(name = "报表中心-库存库龄")
@RestController
@RequestMapping("/report/inventory")
@RequiredArgsConstructor
public class ReportInventoryController {

    private final ReportQueryService reportQueryService;
    private final ReportStandardService reportStandardService;

    /**
     * 库龄分析：按仓+物料最后一次入库流水时间分桶
     * （0-30/31-90/91-180/180+ 天的库存量与占比 + 明细）；export=csv 导出明细。
     */
    @Operation(summary = "库存库龄分析（export=csv 导出明细）")
    @RequirePerm("report:aging:list")
    @GetMapping("/aging")
    public Object aging(@RequestParam(required = false) String export,
                        HttpServletResponse response) throws IOException {
        InventoryAgingVO vo = reportQueryService.inventoryAging();
        if (!"csv".equalsIgnoreCase(export)) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("仓库", "物料编码", "物料名称", "现存量", "加权价", "库存价值",
                "最后入库时间", "库龄天数", "库龄分桶");
        List<List<String>> data = new ArrayList<>(vo.getDetails().size());
        for (InventoryAgingVO.AgingDetailVO d : vo.getDetails()) {
            data.add(List.of(
                    CsvExporter.nvl(d.getWarehouseName()),
                    CsvExporter.nvl(d.getMaterialCode()),
                    CsvExporter.nvl(d.getMaterialName()),
                    CsvExporter.plain(d.getQty()),
                    CsvExporter.plain(d.getPrice()),
                    CsvExporter.plain(d.getValue()),
                    CsvExporter.nvl(d.getLastInTime()),
                    d.getAgeDays() == null ? "" : d.getAgeDays().toString(),
                    CsvExporter.nvl(d.getBucketLabel())));
        }
        CsvExporter.export(response, CsvExporter.filename("inventory_aging", "all"), headers, data);
        return null;
    }

    /**
     * 收发存月报：month=yyyy-MM（默认当月）。物料 × 期初/入库/出库/期末（数量+金额，
     * 金额取 cost_flow 期间汇总），满足 期初+入−出=期末；export=csv 导出明细+合计行。
     */
    @Operation(summary = "收发存月报（export=csv 导出）")
    @RequirePerm("report:stock:list")
    @GetMapping("/monthly")
    public Object monthly(@ModelAttribute InventoryMonthlyQuery query,
                          HttpServletResponse response) throws IOException {
        InventoryMonthlyVO vo = reportStandardService.inventoryMonthly(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("物料编码", "物料名称", "单位",
                "期初数量", "期初金额(元)", "入库数量", "入库金额(元)",
                "出库数量", "出库金额(元)", "期末数量", "期末金额(元)");
        List<List<String>> data = new ArrayList<>(vo.getRows().size() + 1);
        for (InventoryMonthlyVO.Row r : vo.getRows()) {
            data.add(List.of(
                    CsvExporter.nvl(r.getMaterialCode()),
                    CsvExporter.nvl(r.getMaterialName()),
                    CsvExporter.nvl(r.getUnitName()),
                    CsvExporter.plain(r.getBeginQty()),
                    CsvExporter.plain(r.getBeginAmount()),
                    CsvExporter.plain(r.getInQty()),
                    CsvExporter.plain(r.getInAmount()),
                    CsvExporter.plain(r.getOutQty()),
                    CsvExporter.plain(r.getOutAmount()),
                    CsvExporter.plain(r.getEndQty()),
                    CsvExporter.plain(r.getEndAmount())));
        }
        InventoryMonthlyVO.Total t = vo.getTotal();
        data.add(List.of("合计", "", "",
                CsvExporter.plain(t.getBeginQty()), CsvExporter.plain(t.getBeginAmount()),
                CsvExporter.plain(t.getInQty()), CsvExporter.plain(t.getInAmount()),
                CsvExporter.plain(t.getOutQty()), CsvExporter.plain(t.getOutAmount()),
                CsvExporter.plain(t.getEndQty()), CsvExporter.plain(t.getEndAmount())));
        CsvExporter.export(response, CsvExporter.filename("inventory_monthly",
                vo.getMonth().replace("-", "")), headers, data);
        return null;
    }

    /**
     * 呆滞物料：days=90（默认）。现存量&gt;0 且 N 天无出库流水的物料清单
     * （库龄/金额/占比）；export=csv 导出明细。
     */
    @Operation(summary = "呆滞物料分析（export=csv 导出）")
    @RequirePerm("report:stock:list")
    @GetMapping("/sluggish")
    public Object sluggish(@ModelAttribute SluggishQuery query,
                           HttpServletResponse response) throws IOException {
        SluggishVO vo = reportStandardService.sluggishMaterials(query);
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("物料编码", "物料名称", "单位", "现存量", "加权价",
                "呆滞金额(元)", "金额占比", "最后出库时间", "无出库天数", "最后入库时间", "库龄天数");
        List<List<String>> data = new ArrayList<>(vo.getRows().size());
        for (SluggishVO.Row r : vo.getRows()) {
            data.add(List.of(
                    CsvExporter.nvl(r.getMaterialCode()),
                    CsvExporter.nvl(r.getMaterialName()),
                    CsvExporter.nvl(r.getUnitName()),
                    CsvExporter.plain(r.getQty()),
                    CsvExporter.plain(r.getPrice()),
                    CsvExporter.plain(r.getValue()),
                    CsvExporter.plain(r.getRatio()),
                    CsvExporter.nvl(r.getLastOutTime()),
                    r.getDaysSinceOut() == null ? "从未出库" : r.getDaysSinceOut().toString(),
                    CsvExporter.nvl(r.getLastInTime()),
                    r.getAgeDays() == null ? "" : r.getAgeDays().toString()));
        }
        CsvExporter.export(response, CsvExporter.filename("inventory_sluggish",
                String.valueOf(vo.getDays())), headers, data);
        return null;
    }
}
