package com.erp.inventory.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.form.WmsTaskCompleteForm;
import com.erp.inventory.dto.form.WmsTaskManualForm;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.WmsKpiVO;
import com.erp.inventory.dto.vo.WmsTaskVO;
import com.erp.inventory.service.WmsTaskService;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * WMS 作业任务接口（V421）：任务分页/看板/领取/开始/完成/取消/手工创建 + 库内 KPI。
 */
@Tag(name = "库存管理-WMS作业任务")
@RestController
@RequestMapping("/inventory/wms")
@RequiredArgsConstructor
public class WmsTaskController {

    private final WmsTaskService taskService;

    /** 任务分页（类型/状态/仓库/执行人/关键词） */
    @Operation(summary = "作业任务分页")
    @RequirePerm("inventory:wms:task:list")
    @GetMapping("/tasks")
    public Result<PageResult<WmsTaskVO>> page(WmsTaskQuery query) {
        return Result.ok(taskService.page(query));
    }

    /** 任务看板：按 (类型, 状态) 分组的未完成任务计数 */
    @Operation(summary = "任务看板(按类型状态计数)")
    @RequirePerm("inventory:wms:task:list")
    @GetMapping("/tasks/board")
    public Result<List<Map<String, Object>>> board(@RequestParam(required = false) Long warehouseId) {
        return Result.ok(taskService.board(warehouseId));
    }

    /** 库内作业 KPI（任务完成量/人效/及时率/库存准确率） */
    @Operation(summary = "库内作业KPI")
    @RequirePerm("inventory:wms:task:list")
    @GetMapping("/kpi")
    public Result<WmsKpiVO> kpi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(taskService.kpi(from, to));
    }

    /** 手工创建任务（MOVE/REPLENISH/PICK） */
    @Operation(summary = "手工创建作业任务")
    @RequirePerm("inventory:wms:task:edit")
    @OperLog(module = "WMS作业", operation = "手工创建任务")
    @Idempotent
    @PostMapping("/tasks")
    public Result<Map<String, Long>> createManual(@Valid @RequestBody WmsTaskManualForm form) {
        return Result.ok(Map.of("id", taskService.createManual(form)));
    }

    /** 领取任务（待分配→待执行，执行人=当前用户） */
    @Operation(summary = "领取任务")
    @RequirePerm("inventory:wms:task:edit")
    @OperLog(module = "WMS作业", operation = "领取任务")
    @Idempotent
    @PostMapping("/tasks/{id}/claim")
    public Result<Void> claim(@PathVariable Long id) {
        taskService.claim(id);
        return Result.ok();
    }

    /** 开始任务（待执行→执行中，可选步骤） */
    @Operation(summary = "开始任务")
    @RequirePerm("inventory:wms:task:edit")
    @OperLog(module = "WMS作业", operation = "开始任务")
    @Idempotent
    @PostMapping("/tasks/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        taskService.start(id);
        return Result.ok();
    }

    /** 完成任务（含库存事务；actualQty 支持部分完成） */
    @Operation(summary = "完成任务(含库存事务)")
    @RequirePerm("inventory:wms:task:audit")
    @OperLog(module = "WMS作业", operation = "完成任务")
    @Idempotent
    @PostMapping("/tasks/{id}/complete")
    public Result<Void> complete(@PathVariable Long id,
                                 @RequestBody(required = false) WmsTaskCompleteForm form) {
        taskService.complete(id, form);
        return Result.ok();
    }

    /** 取消任务 */
    @Operation(summary = "取消任务")
    @RequirePerm("inventory:wms:task:edit")
    @OperLog(module = "WMS作业", operation = "取消任务")
    @Idempotent
    @DeleteMapping("/tasks/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id);
        return Result.ok();
    }

    /** 手动触发一轮扫描（调试/补生成用；平时由 30s 定时器驱动） */
    @Operation(summary = "手动触发扫描器")
    @RequirePerm("inventory:wms:task:edit")
    @OperLog(module = "WMS作业", operation = "手动触发扫描器")
    @Idempotent
    @PostMapping("/tasks/scan")
    public Result<Map<String, Integer>> scan() {
        return Result.ok(Map.of("created", taskService.scanOnce()));
    }
}
