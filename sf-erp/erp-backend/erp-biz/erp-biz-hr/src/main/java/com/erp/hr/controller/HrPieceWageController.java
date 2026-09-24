package com.erp.hr.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.hr.dto.vo.PieceWageCollectResultVO;
import com.erp.hr.dto.vo.PieceWageDetailVO;
import com.erp.hr.dto.vo.PieceWageEmployeeSummaryVO;
import com.erp.hr.dto.vo.PieceWageVO;
import com.erp.hr.service.PieceWageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 计件工资接口：期间列表、归集（幂等重建）、结账、明细/按员工汇总。
 */
@Tag(name = "人力资源-计件工资")
@RestController
@RequestMapping("/hr/piece-wages")
@RequiredArgsConstructor
public class HrPieceWageController {

    private final PieceWageService wageService;

    /** 期间分页（附明细数/合计金额） */
    @Operation(summary = "计件期间分页")
    @RequirePerm("hr:piecewage:list")
    @GetMapping
    public Result<PageResult<PieceWageVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(wageService.page(pageNum, pageSize));
    }

    /** 期间详情 */
    @Operation(summary = "计件期间详情")
    @RequirePerm("hr:piecewage:list")
    @GetMapping("/{period}")
    public Result<PieceWageVO> detail(@PathVariable String period) {
        return Result.ok(wageService.detail(period));
    }

    /** 期间明细分页（可按员工过滤） */
    @Operation(summary = "计件明细分页")
    @RequirePerm("hr:piecewage:list")
    @GetMapping("/{period}/details")
    public Result<PageResult<PieceWageDetailVO>> details(
            @PathVariable String period,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(wageService.details(period, employeeId, pageNum, pageSize));
    }

    /** 期间按员工合计 */
    @Operation(summary = "计件按员工合计")
    @RequirePerm("hr:piecewage:list")
    @GetMapping("/{period}/summary")
    public Result<List<PieceWageEmployeeSummaryVO>> employeeSummary(@PathVariable String period) {
        return Result.ok(wageService.employeeSummary(period));
    }

    /** 归集：按价目表+完工订单生成本期计件明细（期间不存在自动开账；幂等重建） */
    @Operation(summary = "归集计件工资")
    @RequirePerm("hr:piecewage:add")
    @OperLog(module = "人力资源", operation = "汇总计件工资")
    @Idempotent
    @PostMapping("/{period}/collect")
    public Result<PieceWageCollectResultVO> collect(@PathVariable String period) {
        return Result.ok(wageService.collect(period));
    }

    /** 结账：OPEN → CLOSED，结账后拒绝再归集 */
    @Operation(summary = "期间结账")
    @RequirePerm("hr:piecewage:audit")
    @OperLog(module = "人力资源", operation = "关闭计件工资期间")
    @Idempotent
    @PostMapping("/{period}/close")
    public Result<Void> close(@PathVariable String period) {
        wageService.close(period);
        return Result.ok();
    }
}
