package com.erp.cost.controller;

import com.erp.common.result.Result;
import com.erp.cost.dto.vo.SettleSummaryVO;
import com.erp.cost.dto.vo.TrendPointVO;
import com.erp.cost.service.CostAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 成本分析接口：物料收发存汇总 + 月度趋势。
 */
@Tag(name = "成本分析")
@RestController
@RequestMapping("/cost/analysis")
@RequiredArgsConstructor
public class CostAnalysisController {

    private final CostAnalysisService analysisService;

    /** 物料收发存汇总（period 必填 yyyyMM） */
    @Operation(summary = "物料收发存汇总")
    @RequirePerm("cost:analysis:list")
    @GetMapping("/materials")
    public Result<SettleSummaryVO> materials(@RequestParam String period) {
        return Result.ok(analysisService.materialSummary(period));
    }

    /** 月度趋势：主营业务成本 / 生产领料成本 / 出入库金额 / 期末存货余额 */
    @Operation(summary = "月度成本趋势")
    @RequirePerm("cost:analysis:list")
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(@RequestParam String from, @RequestParam String to) {
        return Result.ok(analysisService.trend(from, to));
    }
}
