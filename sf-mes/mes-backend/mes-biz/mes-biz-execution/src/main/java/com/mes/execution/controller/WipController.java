package com.mes.execution.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.execution.dto.vo.WipVO;
import com.mes.execution.service.WipService;
import com.mes.framework.security.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在制品（WIP）查询接口。
 */
@Tag(name = "执行-在制品")
@RestController
@RequestMapping("/execution/wips")
@RequiredArgsConstructor
public class WipController {

    private final WipService wipService;

    /**
     * 分页查询 WIP 汇总。
     */
    @Operation(summary = "分页查询WIP")
    @RequirePerm("execution:wip:list")
    @GetMapping
    public Result<PageResult<WipVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "10") long pageSize,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long workorderId) {
        return Result.ok(wipService.page(pageNum, pageSize, keyword, workorderId));
    }
}
