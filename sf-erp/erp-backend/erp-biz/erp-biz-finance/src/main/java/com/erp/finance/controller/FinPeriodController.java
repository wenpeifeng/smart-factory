package com.erp.finance.controller;

import com.erp.common.result.Result;
import com.erp.finance.dto.vo.PeriodVO;
import com.erp.finance.service.FinPeriodService;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 总账会计期间接口（V521）：期间列表 + 结账/反结账。
 * <p>
 * 期间 = 凭证日期所在自然月（yyyyMM），按账套隔离；
 * CLOSED 期间禁止凭证新增/修改/过账/删除（FinVoucherService 四入口校验）。
 */
@Tag(name = "财务管理-会计期间")
@RestController
@RequestMapping("/finance/periods")
@RequiredArgsConstructor
public class FinPeriodController {

    private final FinPeriodService periodService;

    @Operation(summary = "期间列表（含该期凭证数/未过账数提示，期间倒序）")
    @RequirePerm("finance:period:list")
    @GetMapping
    public Result<List<PeriodVO>> list() {
        return Result.ok(periodService.list());
    }

    @Operation(summary = "结账（该期凭证须全部过账，存在 DRAFT 拒绝并提示数量）")
    @RequirePerm("finance:period:audit")
    @OperLog(module = "财务管理", operation = "期间结账")
    @Idempotent
    @PostMapping("/{period}/close")
    public Result<Void> close(@PathVariable String period) {
        periodService.close(period);
        return Result.ok();
    }

    @Operation(summary = "反结账（CLOSED → OPEN，记录操作人与时间）")
    @RequirePerm("finance:period:audit")
    @OperLog(module = "财务管理", operation = "期间反结账")
    @Idempotent
    @PostMapping("/{period}/reopen")
    public Result<Void> reopen(@PathVariable String period) {
        periodService.reopen(period);
        return Result.ok();
    }
}
