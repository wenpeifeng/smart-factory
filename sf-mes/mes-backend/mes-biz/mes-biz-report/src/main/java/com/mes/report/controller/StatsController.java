package com.mes.report.controller;

import com.mes.common.result.Result;
import com.mes.framework.security.RequirePerm;
import com.mes.report.dto.vo.CockpitVO;
import com.mes.report.dto.vo.ScreenVO;
import com.mes.report.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报表/驾驶舱/大屏接口（只读聚合；CSV 导出直接写响应流）。
 */
@Tag(name = "报表-统计")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 车间电子看板大屏数据（30 秒轮询）。
     */
    @Operation(summary = "看板大屏数据")
    @RequirePerm("report:screen:query")
    @GetMapping("/screen")
    public Result<ScreenVO> screen() {
        return Result.ok(statsService.screen());
    }

    /**
     * 管理驾驶舱数据。
     */
    @Operation(summary = "管理驾驶舱数据")
    @RequirePerm("report:cockpit:query")
    @GetMapping("/cockpit")
    public Result<CockpitVO> cockpit() {
        return Result.ok(statsService.cockpit());
    }

    /**
     * 工单达成报表（JSON，前端表格 + CSV 导出）。
     */
    @Operation(summary = "工单达成报表")
    @RequirePerm("report:workorder:query")
    @GetMapping("/workorder-achievement")
    public Result<List<Map<String, Object>>> workorders(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return Result.ok(statsService.workorderAchievement(from, to));
    }

    /**
     * 工单达成报表 CSV 导出。
     */
    @Operation(summary = "工单达成CSV")
    @RequirePerm("report:workorder:export")
    @GetMapping("/workorder-achievement/csv")
    public void workorderCsv(HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = statsService.workorderAchievement(null, null);
        writeCsv(response, "工单达成报表", rows,
                "woNo,materialName,planQty,finishQty,scrapQty,status,planDate",
                "工单号,产品,计划数量,完工数量,报废数量,状态,计划开工");
    }

    /**
     * 报工明细报表。
     */
    @Operation(summary = "报工明细报表")
    @RequirePerm("report:station:query")
    @GetMapping("/station-detail")
    public Result<List<Map<String, Object>>> stations(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.stationDetail(days));
    }

    /**
     * 报工明细 CSV 导出。
     */
    @Operation(summary = "报工明细CSV")
    @RequirePerm("report:station:export")
    @GetMapping("/station-detail/csv")
    public void stationCsv(HttpServletResponse response,
                           @RequestParam(defaultValue = "30") int days) throws IOException {
        List<Map<String, Object>> rows = statsService.stationDetail(days);
        writeCsv(response, "报工明细", rows,
                "recordNo,woNo,operationName,goodQty,scrapQty,reworkQty,workerName,stationTime,result",
                "记录号,工单号,工序,合格,报废,返修,报工人,过站时间,结果");
    }

    /**
     * 质量统计报表（按物料）。
     */
    @Operation(summary = "质量统计报表")
    @RequirePerm("report:quality:query")
    @GetMapping("/quality-summary")
    public Result<List<Map<String, Object>>> quality() {
        return Result.ok(statsService.qualityByMaterial());
    }

    /**
     * 通用 CSV 输出（BOM 头 + 逗号转义，Excel 可直接打开）。
     */
    private void writeCsv(HttpServletResponse response, String filename,
                          List<Map<String, Object>> rows, String keys, String header) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(filename + ".csv", StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("\uFEFF").append(header).append('\n');
        String[] keyArr = keys.split(",");
        for (Map<String, Object> row : rows) {
            String[] cells = new String[keyArr.length];
            for (int i = 0; i < keyArr.length; i++) {
                Object v = row.get(keyArr[i]);
                String cell = v == null ? "" : String.valueOf(v);
                cells[i] = cell.contains(",") || cell.contains("\n") ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
            }
            sb.append(String.join(",", cells)).append('\n');
        }
        response.getWriter().write(sb.toString());
    }
}
