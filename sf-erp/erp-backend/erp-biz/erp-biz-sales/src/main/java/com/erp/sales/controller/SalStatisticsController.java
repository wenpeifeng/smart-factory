package com.erp.sales.controller;

import com.erp.common.result.Result;
import com.erp.sales.dto.vo.SalDailyStatVO;
import com.erp.sales.dto.vo.SalRankVO;
import com.erp.sales.service.SalStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 销售统计接口（口径：仅"已审核"销售出库单；成本取 base_material.ref_cost）。
 */
@Tag(name = "销售统计")
@RestController
@RequestMapping("/sales/statistics")
@RequiredArgsConstructor
public class SalStatisticsController {

    private final SalStatisticsService statisticsService;

    /** 按日汇总销售额/成本/毛利 */
    @Operation(summary = "按日销售统计")
    @RequirePerm("sales:statistics:list")
    @GetMapping("/daily")
    public Result<List<SalDailyStatVO>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statisticsService.dailyStats(from, to));
    }

    /** 销售排行 TOP10（type=material 物料 / customer 客户） */
    @Operation(summary = "销售排行 TOP10")
    @RequirePerm("sales:statistics:list")
    @GetMapping("/rank")
    public Result<List<SalRankVO>> rank(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statisticsService.rank(type, from, to));
    }
}
