package com.erp.mrp.controller;

import com.erp.common.result.Result;
import com.erp.mrp.dto.form.WorkReportForm;
import com.erp.mrp.dto.vo.WorkReportVO;
import com.erp.mrp.service.MrpWorkReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 工序报工接口 —— 报工明细 + 回写工序任务状态。
 */
@Tag(name = "生产制造-报工")
@RestController
@RequestMapping("/mrp/report")
@RequiredArgsConstructor
public class MrpWorkReportController {

    private final MrpWorkReportService workReportService;

    /** 工序报工 */
    @Operation(summary = "工序报工")
    @RequirePerm("mrp:workorder:add")
    @OperLog(module = "生产制造", operation = "工序报工")
    @Idempotent
    @PostMapping
    public Result<Long> report(@Valid @RequestBody WorkReportForm form) {
        return Result.ok(workReportService.report(form));
    }

    /** 报工明细查询（woStepId / workOrderId 过滤） */
    @Operation(summary = "报工明细")
    @RequirePerm("mrp:workorder:list")
    @GetMapping("/list")
    public Result<List<WorkReportVO>> list(
            @RequestParam(required = false) Long woStepId,
            @RequestParam(required = false) Long workOrderId) {
        return Result.ok(workReportService.list(woStepId, workOrderId));
    }
}
