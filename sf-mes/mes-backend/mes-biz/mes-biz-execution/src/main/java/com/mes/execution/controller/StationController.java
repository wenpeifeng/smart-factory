package com.mes.execution.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.execution.dto.form.StationReportForm;
import com.mes.execution.dto.query.StationRecordQuery;
import com.mes.execution.dto.vo.StationRecordVO;
import com.mes.execution.service.StationService;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 过站扫码报工接口（车间终端 + 移动端 /m 共用）。
 */
@Tag(name = "执行-过站报工")
@RestController
@RequestMapping("/execution/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    /**
     * 过站扫码报工。
     */
    @Operation(summary = "过站扫码报工")
    @RequirePerm("execution:station:report")
    @OperLog(module = "过站报工", operation = "过站扫码报工")
    @Idempotent
    @PostMapping
    public Result<Long> report(@Valid @RequestBody StationReportForm form) {
        return Result.ok(stationService.report(form));
    }

    /**
     * 分页查询过站记录。
     */
    @Operation(summary = "分页查询过站记录")
    @RequirePerm("execution:station:list")
    @GetMapping
    public Result<PageResult<StationRecordVO>> page(StationRecordQuery query) {
        return Result.ok(stationService.page(query));
    }
}
