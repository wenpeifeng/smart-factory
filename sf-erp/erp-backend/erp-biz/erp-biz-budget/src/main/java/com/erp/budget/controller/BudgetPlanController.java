package com.erp.budget.controller;

import com.erp.budget.dto.form.BudgetControlConfigForm;
import com.erp.budget.dto.form.BudgetMatrixForm;
import com.erp.budget.dto.form.BudgetPlanForm;
import com.erp.budget.dto.query.BudgetPlanQuery;
import com.erp.budget.dto.vo.BudgetMatrixVO;
import com.erp.budget.dto.vo.BudgetPlanVO;
import com.erp.budget.service.BudgetPlanService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
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
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 预算方案接口 —— 方案生命周期 + 部门×科目×12 月明细矩阵 + 控制配置。
 * <p>
 * 状态机：DRAFT(可编制) → RELEASED(锁定禁改) → CLOSED；
 * 写操作（下达/关闭/矩阵保存）均为状态流转类操作，挂幂等注解防连点。
 */
@Tag(name = "预算管理-预算方案")
@RestController
@RequestMapping("/budget/plans")
@RequiredArgsConstructor
public class BudgetPlanController {

    private final BudgetPlanService planService;

    /** 分页查询方案（关键词/年度/状态） */
    @Operation(summary = "分页查询预算方案")
    @RequirePerm("budget:plan:list")
    @GetMapping
    public Result<PageResult<BudgetPlanVO>> page(BudgetPlanQuery query) {
        return Result.ok(planService.page(query));
    }

    /** 方案详情（含控制配置与编制人） */
    @Operation(summary = "预算方案详情")
    @RequirePerm("budget:plan:list")
    @GetMapping("/{id}")
    public Result<BudgetPlanVO> detail(@PathVariable Long id) {
        return Result.ok(planService.getDetail(id));
    }

    /** 新增方案（初始草稿 + 默认控制配置 WARN/100%） */
    @Operation(summary = "新增预算方案")
    @RequirePerm("budget:plan:add")
    @OperLog(module = "预算管理", operation = "新增预算计划")
    @PostMapping
    @Idempotent
    public Result<Long> create(@Valid @RequestBody BudgetPlanForm form) {
        return Result.ok(planService.create(form));
    }

    /** 修改方案基本信息（仅草稿） */
    @Operation(summary = "修改预算方案")
    @RequirePerm("budget:plan:edit")
    @OperLog(module = "预算管理", operation = "修改预算计划")
    @PutMapping("/{id}")
    @Idempotent
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BudgetPlanForm form) {
        planService.update(id, form);
        return Result.ok();
    }

    /** 删除方案（仅草稿，连带明细/配置逻辑删） */
    @Operation(summary = "删除预算方案")
    @RequirePerm("budget:plan:delete")
    @OperLog(module = "预算管理", operation = "删除预算计划")
    @DeleteMapping("/{id}")
    @Idempotent
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }

    /** 下达方案（草稿→已下达，明细锁定） */
    @Operation(summary = "下达预算方案")
    @RequirePerm("budget:plan:audit")
    @OperLog(module = "预算管理", operation = "发布预算")
    @PostMapping("/{id}/release")
    @Idempotent
    public Result<Void> release(@PathVariable Long id) {
        planService.release(id);
        return Result.ok();
    }

    /** 关闭方案（已下达→已关闭） */
    @Operation(summary = "关闭预算方案")
    @RequirePerm("budget:plan:audit")
    @OperLog(module = "预算管理", operation = "关闭预算")
    @PostMapping("/{id}/close")
    @Idempotent
    public Result<Void> close(@PathVariable Long id) {
        planService.close(id);
        return Result.ok();
    }

    /** 复制方案（默认复制为下一年度草稿，含明细与控制配置） */
    @Operation(summary = "复制预算方案(上年/指定年度)")
    @RequirePerm("budget:plan:add")
    @OperLog(module = "预算管理", operation = "复制预算计划")
    @PostMapping("/{id}/copy")
    @Idempotent
    public Result<Long> copy(@PathVariable Long id,
                             @RequestParam(value = "targetYear", required = false) Integer targetYear) {
        return Result.ok(planService.copyPlan(id, targetYear));
    }

    /** 读取明细矩阵（部门×科目×12月 + 行列合计） */
    @Operation(summary = "预算明细矩阵查询")
    @RequirePerm("budget:plan:list")
    @GetMapping("/{id}/matrix")
    public Result<BudgetMatrixVO> matrix(@PathVariable Long id) {
        return Result.ok(planService.getMatrix(id));
    }

    /** 保存明细矩阵（仅草稿；整体提交） */
    @Operation(summary = "保存预算明细矩阵")
    @RequirePerm("budget:plan:edit")
    @OperLog(module = "预算管理", operation = "保存预算矩阵")
    @PutMapping("/{id}/matrix")
    @Idempotent
    public Result<Void> saveMatrix(@PathVariable Long id, @Valid @RequestBody BudgetMatrixForm form) {
        planService.saveMatrix(id, form);
        return Result.ok();
    }

    /** 保存控制配置（草稿/已下达可调，已关闭禁改） */
    @Operation(summary = "保存预算控制配置")
    @RequirePerm("budget:plan:edit")
    @OperLog(module = "预算管理", operation = "保存预算控制配置")
    @PutMapping("/{id}/control-config")
    @Idempotent
    public Result<Void> saveControlConfig(@PathVariable Long id,
                                          @Valid @RequestBody BudgetControlConfigForm form) {
        planService.saveControlConfig(id, form);
        return Result.ok();
    }
}
