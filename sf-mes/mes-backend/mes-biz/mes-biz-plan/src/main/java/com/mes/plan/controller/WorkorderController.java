package com.mes.plan.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.plan.dto.form.WorkorderForm;
import com.mes.plan.dto.query.WorkorderPageQuery;
import com.mes.plan.dto.vo.WorkorderDetailVO;
import com.mes.plan.dto.vo.WorkorderVO;
import com.mes.plan.service.WorkorderService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产工单接口。
 */
@Tag(name = "计划-生产工单")
@RestController
@RequestMapping("/plan/workorders")
@RequiredArgsConstructor
public class WorkorderController {

    private final WorkorderService workorderService;

    /**
     * 分页查询工单。
     */
    @Operation(summary = "分页查询工单")
    @RequirePerm("plan:workorder:list")
    @GetMapping
    public Result<PageResult<WorkorderVO>> page(WorkorderPageQuery query) {
        return Result.ok(workorderService.page(query));
    }

    /**
     * 工单详情（含工序行/用料行）。
     */
    @Operation(summary = "工单详情")
    @RequirePerm("plan:workorder:list")
    @GetMapping("/{id}")
    public Result<WorkorderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(workorderService.detail(id));
    }

    /**
     * 手工创建工单。
     */
    @Operation(summary = "创建工单")
    @RequirePerm("plan:workorder:add")
    @OperLog(module = "生产工单", operation = "创建工单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WorkorderForm form) {
        return Result.ok(workorderService.create(form));
    }

    /**
     * 修改工单（仅草稿）。
     */
    @Operation(summary = "修改工单")
    @RequirePerm("plan:workorder:edit")
    @OperLog(module = "生产工单", operation = "修改工单")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WorkorderForm form) {
        workorderService.update(id, form);
        return Result.ok();
    }

    /**
     * 删除工单（仅草稿）。
     */
    @Operation(summary = "删除工单")
    @RequirePerm("plan:workorder:delete")
    @OperLog(module = "生产工单", operation = "删除工单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workorderService.delete(id);
        return Result.ok();
    }

    /**
     * 下达工单（展开 BOM 用料 + 工艺路线工序）。
     */
    @Operation(summary = "下达工单")
    @RequirePerm("plan:workorder:release")
    @OperLog(module = "生产工单", operation = "下达工单")
    @Idempotent
    @PutMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        workorderService.release(id);
        return Result.ok();
    }

    /**
     * 工单完工。
     */
    @Operation(summary = "工单完工")
    @RequirePerm("plan:workorder:finish")
    @OperLog(module = "生产工单", operation = "工单完工")
    @Idempotent
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        workorderService.finish(id);
        return Result.ok();
    }

    /**
     * 工单关闭（结案）。
     */
    @Operation(summary = "关闭工单")
    @RequirePerm("plan:workorder:close")
    @OperLog(module = "生产工单", operation = "关闭工单")
    @Idempotent
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestParam(required = false) String closeRemark) {
        workorderService.close(id, closeRemark);
        return Result.ok();
    }
}
