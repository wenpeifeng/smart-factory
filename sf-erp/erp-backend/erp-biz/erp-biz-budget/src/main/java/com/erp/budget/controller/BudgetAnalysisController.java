package com.erp.budget.controller;

import com.erp.budget.dto.vo.BudgetAnalysisRowVO;
import com.erp.budget.dto.vo.BudgetMonthlyCurveVO;
import com.erp.budget.service.BudgetAnalysisService;
import com.erp.common.result.Result;
import com.erp.framework.ratelimit.RateLimit;
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
 * 预算执行分析接口 —— 部门×科目 预算/已用/余额/执行率 + 月度执行曲线。
 * <p>
 * 统计类重查询，挂限流（每 10 秒 30 次/用户）防刷。
 */
@Tag(name = "预算管理-执行分析")
@RestController
@RequestMapping("/budget/analysis")
@RequiredArgsConstructor
public class BudgetAnalysisController {

    private final BudgetAnalysisService analysisService;

    /** 部门×科目执行分析（全年口径，执行率>100 由前端标红） */
    @Operation(summary = "部门x科目执行分析")
    @RequirePerm("budget:analysis:list")
    @GetMapping
    @RateLimit(windowSeconds = 10, maxRequests = 30)
    public Result<List<BudgetAnalysisRowVO>> analysis(
            @RequestParam Long planId,
            @RequestParam(value = "deptId", required = false) Long deptId) {
        return Result.ok(analysisService.analysis(planId, deptId));
    }

    /** 月度执行曲线数据（1-12 月预算/已用/执行率，供 Recharts 折线图） */
    @Operation(summary = "月度执行曲线数据")
    @RequirePerm("budget:analysis:list")
    @GetMapping("/monthly")
    @RateLimit(windowSeconds = 10, maxRequests = 30)
    public Result<List<BudgetMonthlyCurveVO>> monthlyCurve(
            @RequestParam Long planId,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "subjectId", required = false) Long subjectId) {
        return Result.ok(analysisService.monthlyCurve(planId, deptId, subjectId));
    }
}
