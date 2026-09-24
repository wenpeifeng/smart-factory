package com.erp.cost.controller;

import com.erp.common.result.Result;
import com.erp.cost.dto.vo.CostPeriodVO;
import com.erp.cost.dto.vo.SettleResultVO;
import com.erp.cost.dto.vo.SettleSummaryVO;
import com.erp.cost.service.CostSettleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 存货月末核算接口：期间列表 / 结账（自动转账凭证）/ 收发存汇总。
 */
@Tag(name = "存货月末核算")
@RestController
@RequestMapping("/cost")
@RequiredArgsConstructor
public class CostSettleController {

    private final CostSettleService settleService;

    /** 全部核算期间列表（倒序） */
    @Operation(summary = "核算期间列表")
    @RequirePerm("cost:period:list")
    @GetMapping("/periods")
    public Result<List<CostPeriodVO>> periods() {
        return Result.ok(settleService.periods());
    }

    /** 月末结账：汇总出库成本 → 自动生成转账凭证 → 期间 CLOSED */
    @Operation(summary = "月末结账")
    @RequirePerm("cost:period:audit")
    @OperLog(module = "成本管理", operation = "成本结账")
    @Idempotent
    @PostMapping("/settle/{period}")
    public Result<SettleResultVO> settle(@PathVariable String period) {
        return Result.ok(settleService.settle(period));
    }

    /** 收发存汇总（期初/入库/出库/期末，按物料 + 合计） */
    @Operation(summary = "期间收发存汇总")
    @RequirePerm("cost:period:list")
    @GetMapping("/settle/summary/{period}")
    public Result<SettleSummaryVO> summary(@PathVariable String period) {
        return Result.ok(settleService.summary(period));
    }
}
