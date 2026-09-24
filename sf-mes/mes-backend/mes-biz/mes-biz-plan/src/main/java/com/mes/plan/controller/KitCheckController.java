package com.mes.plan.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.plan.dto.vo.KitCheckDetailVO;
import com.mes.plan.dto.vo.KitCheckVO;
import com.mes.plan.service.KitCheckService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 齐套分析接口。
 */
@Tag(name = "计划-齐套分析")
@RestController
@RequestMapping("/plan/kit-checks")
@RequiredArgsConstructor
public class KitCheckController {

    private final KitCheckService kitCheckService;

    /**
     * 对工单执行齐套分析。
     */
    @Operation(summary = "执行齐套分析")
    @RequirePerm("plan:kitcheck:run")
    @OperLog(module = "齐套分析", operation = "执行齐套分析")
    @Idempotent
    @PostMapping("/{workorderId}")
    public Result<Long> check(@PathVariable Long workorderId) {
        return Result.ok(kitCheckService.check(workorderId));
    }

    /**
     * 分页查询齐套分析记录。
     */
    @Operation(summary = "分页查询齐套分析")
    @RequirePerm("plan:kitcheck:list")
    @GetMapping
    public Result<PageResult<KitCheckVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String result) {
        return Result.ok(kitCheckService.page(pageNum, pageSize, keyword, result));
    }

    /**
     * 工单最近一次齐套分析详情。
     */
    @Operation(summary = "工单最近齐套分析")
    @RequirePerm("plan:kitcheck:list")
    @GetMapping("/latest/{workorderId}")
    public Result<KitCheckDetailVO> latest(@PathVariable Long workorderId) {
        return Result.ok(kitCheckService.latestByWorkorder(workorderId));
    }
}
