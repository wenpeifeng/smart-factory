package com.erp.asset.controller;

import com.erp.asset.dto.vo.DepreciationPeriodDetailVO;
import com.erp.asset.dto.vo.DepreciationPeriodVO;
import com.erp.asset.dto.vo.DepreciationRunVO;
import com.erp.asset.dto.vo.StatisticsVO;
import com.erp.asset.service.AstDepreciationService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 折旧计提与统计接口。
 * <p>
 * 期间 path 参数格式 yyyyMM（如 202609）；同一期间 run 只允许成功执行一次。
 */
@Tag(name = "折旧计提")
@RestController
@RequestMapping("/asset")
@RequiredArgsConstructor
public class AstDepreciationController {

    private final AstDepreciationService depreciationService;

    /** 折旧期间分页（按期间倒序） */
    @Operation(summary = "折旧期间分页")
    @RequirePerm("asset:depreciation:list")
    @GetMapping("/depreciations")
    public Result<PageResult<DepreciationPeriodVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(depreciationService.page(pageNum, pageSize));
    }

    /** 期间详情：期间头 + 逐资产明细 + 合计 */
    @Operation(summary = "期间计提明细")
    @RequirePerm("asset:depreciation:list")
    @GetMapping("/depreciations/{period}")
    public Result<DepreciationPeriodDetailVO> detail(
            @Parameter(description = "会计期间 yyyyMM") @PathVariable String period) {
        return Result.ok(depreciationService.detail(period));
    }

    /** 执行期间计提（重复运行拒绝） */
    @Operation(summary = "执行期间折旧计提")
    @RequirePerm("asset:depreciation:audit")
    @OperLog(module = "资产管理", operation = "计提折旧")
    @Idempotent
    @PostMapping("/depreciations/{period}/run")
    public Result<DepreciationRunVO> run(
            @Parameter(description = "会计期间 yyyyMM") @PathVariable String period) {
        return Result.ok(depreciationService.run(period));
    }

    /** 资产统计：截至期间，部门/类别维度原值/累计折旧/净值汇总 */
    @Operation(summary = "资产统计")
    @RequirePerm("asset:depreciation:list")
    @GetMapping("/statistics")
    public Result<StatisticsVO> statistics(
            @Parameter(description = "会计期间 yyyyMM，缺省当月")
            @RequestParam(required = false) String period) {
        return Result.ok(depreciationService.statistics(period));
    }
}
