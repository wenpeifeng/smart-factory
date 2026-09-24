package com.mes.trace.controller;

import com.mes.common.result.Result;
import com.mes.framework.security.RequirePerm;
import com.mes.trace.dto.vo.TraceNodeVO;
import com.mes.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 正反向追溯接口。
 */
@Tag(name = "追溯-链路查询")
@RestController
@RequestMapping("/trace/links")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    /**
     * 正向追溯：批次/关键件SN → 下游产品。
     */
    @Operation(summary = "正向追溯(批次/SN→产品)")
    @RequirePerm("trace:link:query")
    @GetMapping("/forward")
    public Result<List<TraceNodeVO>> forward(@RequestParam String no) {
        return Result.ok(traceService.traceForward(no));
    }

    /**
     * 反向追溯：产品批次/SN/工单号 → 来料批次与关键件。
     */
    @Operation(summary = "反向追溯(产品→来料/关键件)")
    @RequirePerm("trace:link:query")
    @GetMapping("/backward")
    public Result<List<TraceNodeVO>> backward(@RequestParam String no) {
        return Result.ok(traceService.traceBackward(no));
    }
}
