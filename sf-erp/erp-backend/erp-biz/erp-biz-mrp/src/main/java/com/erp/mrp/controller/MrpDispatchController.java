package com.erp.mrp.controller;

import com.erp.common.result.Result;
import com.erp.mrp.dto.form.DispatchForm;
import com.erp.mrp.dto.vo.DispatchBoardVO;
import com.erp.mrp.dto.vo.WoStepVO;
import com.erp.mrp.service.MrpDispatchService;
import com.erp.mrp.service.MrpWoStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * 派工接口 —— 工序任务看板（按工作中心分组）与派工动作。
 */
@Tag(name = "生产制造-派工")
@RestController
@RequestMapping("/mrp/dispatch")
@RequiredArgsConstructor
public class MrpDispatchController {

    private final MrpWoStepService woStepService;
    private final MrpDispatchService dispatchService;

    /** 工序任务看板（按工作中心分组） */
    @Operation(summary = "工序任务看板")
    @RequirePerm("mrp:dispatch:list")
    @GetMapping("/list")
    public Result<DispatchBoardVO> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long workCenterId) {
        return Result.ok(woStepService.board(status, workCenterId));
    }

    /** 按生产订单查工序任务（工序进度视图） */
    @Operation(summary = "订单工序进度")
    @RequirePerm("mrp:dispatch:list")
    @GetMapping("/wo-steps")
    public Result<List<WoStepVO>> woSteps(@RequestParam Long workOrderId) {
        return Result.ok(woStepService.listByWorkOrder(workOrderId));
    }

    /** 派工（待派工 → 已派工） */
    @Operation(summary = "派工")
    @RequirePerm("mrp:dispatch:edit")
    @OperLog(module = "生产制造", operation = "工序派工")
    @Idempotent
    @PostMapping("/{woStepId}")
    public Result<Void> dispatch(@PathVariable Long woStepId, @Valid @RequestBody DispatchForm form) {
        dispatchService.dispatch(woStepId, form);
        return Result.ok();
    }
}
