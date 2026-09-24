package com.mes.execution.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.execution.dto.form.ReworkScrapForm;
import com.mes.execution.dto.vo.ReworkScrapVO;
import com.mes.execution.service.ReworkScrapService;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 返工/报废单接口。
 */
@Tag(name = "执行-返工报废")
@RestController
@RequestMapping("/execution/rework-scraps")
@RequiredArgsConstructor
public class ReworkScrapController {

    private final ReworkScrapService reworkScrapService;

    /**
     * 创建返工/报废单。
     */
    @Operation(summary = "创建返工/报废单")
    @RequirePerm("execution:reworkscrap:add")
    @OperLog(module = "返工报废", operation = "创建返工/报废单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReworkScrapForm form) {
        return Result.ok(reworkScrapService.create(form));
    }

    /**
     * 处理完成。
     */
    @Operation(summary = "处理返工/报废单")
    @RequirePerm("execution:reworkscrap:handle")
    @OperLog(module = "返工报废", operation = "处理返工/报废单")
    @Idempotent
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestParam(required = false) String handleRemark) {
        reworkScrapService.handle(id, handleRemark);
        return Result.ok();
    }

    /**
     * 分页查询。
     */
    @Operation(summary = "分页查询返工/报废单")
    @RequirePerm("execution:reworkscrap:list")
    @GetMapping
    public Result<PageResult<ReworkScrapVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(reworkScrapService.page(pageNum, pageSize, keyword, type, status));
    }
}
