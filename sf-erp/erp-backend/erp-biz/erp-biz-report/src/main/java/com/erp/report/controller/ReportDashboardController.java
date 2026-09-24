package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.report.dto.vo.DashboardVO;
import com.erp.report.service.ReportDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 经营驾驶舱接口：本期（当月）与环比核心指标 + 走势/分布/TOP。
 */
@Tag(name = "报表中心-驾驶舱")
@RestController
@RequestMapping("/report/dashboard")
@RequiredArgsConstructor
public class ReportDashboardController {

    private final ReportDashboardService dashboardService;

    /** 驾驶舱聚合数据（当月/环比/近6月走势/仓库价值分布/当月销售TOP5） */
    @Operation(summary = "经营驾驶舱聚合数据")
    @RequirePerm("report:dashboard:list")
    @GetMapping
    public Result<DashboardVO> dashboard() {
        return Result.ok(dashboardService.dashboard());
    }
}
