package com.mes.trace.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;
import com.mes.trace.dto.vo.TraceEbrVO;
import com.mes.trace.service.EbrService;
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
 * 电子批记录接口（工单完工快照，合规审查导出用）。
 */
@Tag(name = "追溯-电子批记录")
@RestController
@RequestMapping("/trace/ebrs")
@RequiredArgsConstructor
public class EbrController {

    private final EbrService ebrService;

    /**
     * 为工单生成电子批记录（幂等：已存在直接返回）。
     */
    @Operation(summary = "生成电子批记录")
    @RequirePerm("trace:ebr:generate")
    @OperLog(module = "电子批记录", operation = "生成电子批记录")
    @Idempotent
    @PostMapping("/workorder/{workorderId}")
    public Result<Long> generate(@PathVariable Long workorderId) {
        return Result.ok(ebrService.generate(workorderId));
    }

    /**
     * 分页查询批记录。
     */
    @Operation(summary = "分页查询批记录")
    @RequirePerm("trace:ebr:list")
    @GetMapping
    public Result<PageResult<TraceEbrVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword) {
        return Result.ok(ebrService.page(pageNum, pageSize, keyword));
    }

    /**
     * 批记录详情（JSON 全文）。
     */
    @Operation(summary = "批记录详情")
    @RequirePerm("trace:ebr:list")
    @GetMapping("/{id}")
    public Result<TraceEbrVO> detail(@PathVariable Long id) {
        return Result.ok(ebrService.detail(id));
    }
}
