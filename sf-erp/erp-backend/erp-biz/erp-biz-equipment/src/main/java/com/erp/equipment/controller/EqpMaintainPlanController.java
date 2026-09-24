package com.erp.equipment.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.equipment.dto.form.PlanExecuteForm;
import com.erp.equipment.dto.form.PlanForm;
import com.erp.equipment.dto.request.PlanQuery;
import com.erp.equipment.dto.vo.PlanVO;
import com.erp.equipment.service.EqpMaintainPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 保养计划接口（CRUD + 到期清单 + 执行登记）。
 */
@Tag(name = "设备管理-保养计划")
@RestController
@RequestMapping("/equipment/plans")
@RequiredArgsConstructor
public class EqpMaintainPlanController {

    private final EqpMaintainPlanService planService;

    /** 计划分页（deviceId 精确 / dueOnly 只看到期与已逾期） */
    @Operation(summary = "保养计划分页查询")
    @RequirePerm("equipment:plan:list")
    @GetMapping
    public Result<PageResult<PlanVO>> page(@ModelAttribute PlanQuery query) {
        return Result.ok(planService.page(query));
    }

    /** 到期清单：已到期 + days 天内到期（默认 7，含已逾期），供 workflow 预警直查 */
    @Operation(summary = "保养到期清单")
    @RequirePerm("equipment:plan:list")
    @GetMapping("/due")
    public Result<List<PlanVO>> due(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(planService.duePlans(days));
    }

    /** 新增计划（nextDate = lastDate + cycleDays 服务端计算） */
    @Operation(summary = "新增保养计划")
    @RequirePerm("equipment:plan:add")
    @OperLog(module = "设备管理", operation = "新增保养计划")
    @Idempotent
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody PlanForm form) {
        return Result.ok(Map.of("id", planService.create(form)));
    }

    /** 修改计划（重算 nextDate） */
    @Operation(summary = "修改保养计划")
    @RequirePerm("equipment:plan:edit")
    @OperLog(module = "设备管理", operation = "修改保养计划")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlanForm form) {
        planService.update(id, form);
        return Result.ok();
    }

    /** 删除计划（逻辑删除，历史执行记录保留） */
    @Operation(summary = "删除保养计划")
    @RequirePerm("equipment:plan:delete")
    @OperLog(module = "设备管理", operation = "删除保养计划")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }

    /** 执行一次保养：写执行记录，计划顺延一个周期（请求体可空） */
    @Operation(summary = "执行保养")
    @RequirePerm("equipment:plan:audit")
    @OperLog(module = "设备管理", operation = "执行保养")
    @Idempotent
    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id,
                                @RequestBody(required = false) PlanExecuteForm form) {
        planService.execute(id, form);
        return Result.ok();
    }
}
