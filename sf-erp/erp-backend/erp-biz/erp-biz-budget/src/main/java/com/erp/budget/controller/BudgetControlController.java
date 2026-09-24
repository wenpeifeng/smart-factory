package com.erp.budget.controller;

import com.erp.budget.dto.form.BudgetCheckForm;
import com.erp.budget.dto.form.BudgetUsageRecordForm;
import com.erp.budget.dto.vo.BudgetCheckResult;
import com.erp.budget.dto.vo.BudgetOptionsVO;
import com.erp.budget.service.BudgetControlService;
import com.erp.budget.service.BudgetPlanService;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 预算控制接口 —— 手动校验 + 执行数登记 + 编制基础选项。
 * <p>
 * 付款接线说明：fin_payment 属 finance 模块，本模块不改其代码；
 * 约定 finance 在付款单创建/审核时调用 {@code BudgetControlService.check(...)}
 * 先校验、通过后调 {@code record(...)} 记执行数（详见 docs/modules/budget.md）。
 * 本期提供 POST /budget/check 手动校验 + POST /budget/usage 演示登记。
 */
@Tag(name = "预算管理-预算控制")
@RestController
@RequestMapping("/budget")
@RequiredArgsConstructor
public class BudgetControlController {

    private final BudgetControlService controlService;
    private final BudgetPlanService planService;

    /**
     * 预算占用校验（手动/联调入口）。
     * 超预算且控制方式为 BLOCK 时抛业务异常 → 统一错误响应（code=1000）。
     */
    @Operation(summary = "预算占用校验(付款/费用发生前)")
    @RequirePerm("budget:check:edit")
    @PostMapping("/check")
    public Result<BudgetCheckResult> check(@Valid @RequestBody BudgetCheckForm form) {
        BudgetCheckResult result = controlService.check(
                form.getDeptId(), form.getSubjectId(), form.getYear(), form.getMonth(), form.getAmount());
        return Result.ok(result);
    }

    /** 登记预算执行数（付款推式记账的演示/接线入口，冲销传负数） */
    @Operation(summary = "登记预算执行数(付款接线/演示)")
    @RequirePerm("budget:check:add")
    @OperLog(module = "预算管理", operation = "登记预算执行数")
    @PostMapping("/usage")
    @Idempotent
    public Result<Long> recordUsage(@Valid @RequestBody BudgetUsageRecordForm form) {
        return Result.ok(controlService.record(form));
    }

    /** 编制基础选项：部门 + 末级费用/成本类科目（约定表只读） */
    @Operation(summary = "预算编制基础选项(部门/科目)")
    @GetMapping("/options")
    public Result<BudgetOptionsVO> options() {
        return Result.ok(planService.getOptions());
    }
}
