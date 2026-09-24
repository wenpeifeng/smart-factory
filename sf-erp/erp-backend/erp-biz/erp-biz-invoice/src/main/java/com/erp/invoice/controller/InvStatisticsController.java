package com.erp.invoice.controller;

import com.erp.common.result.Result;
import com.erp.invoice.dto.vo.MonthlyStatVO;
import com.erp.invoice.dto.vo.ReconciliationVO;
import com.erp.invoice.dto.vo.TaxStatVO;
import com.erp.invoice.service.InvStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import com.erp.framework.security.RequirePerm;

/**
 * 税务统计与对账辅助接口：期间税负、月度价税走势、客户/供应商对账。
 */
@Tag(name = "发票管理-税务统计")
@RestController
@RequestMapping("/invoice")
@RequiredArgsConstructor
public class InvStatisticsController {

    private final InvStatisticsService statisticsService;

    /** 期间税负：销项税额 / 进项税额(已认证) / 应纳增值税 / 税负率 */
    @Operation(summary = "期间税负统计")
    @RequirePerm("invoice:tax:list")
    @GetMapping("/statistics/tax")
    public Result<TaxStatVO> tax(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statisticsService.tax(from, to));
    }

    /** 年度 12 个月价税走势（缺省当前年） */
    @Operation(summary = "月度价税走势")
    @RequirePerm("invoice:tax:list")
    @GetMapping("/statistics/monthly")
    public Result<MonthlyStatVO> monthly(@RequestParam(required = false) Integer year) {
        return Result.ok(statisticsService.monthly(year));
    }

    /** 客户/供应商对账辅助：业务金额 vs 开票金额 vs 未开票差额 */
    @Operation(summary = "对账辅助")
    @RequirePerm("invoice:tax:list")
    @GetMapping("/reconciliation")
    public Result<ReconciliationVO> reconciliation(
            @RequestParam String partnerType,
            @RequestParam Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statisticsService.reconciliation(partnerType, partnerId, from, to));
    }
}
