package com.erp.basedata.controller;

import com.erp.basedata.dto.vo.SupplierGradeSummaryVO;
import com.erp.basedata.dto.vo.SupplierScoreVO;
import com.erp.basedata.service.PurSupplierScoreService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商交易评分接口（V141，权限挂采购口径 purchase:supplierScore:*）。
 */
@Tag(name = "采购-供应商评级")
@RestController
@RequestMapping("/purchase/supplier-scores")
@RequiredArgsConstructor
public class SupplierScoreController {

    private final PurSupplierScoreService scoreService;

    /** 评分分页 */
    @Operation(summary = "评分分页")
    @RequirePerm("purchase:supplierScore:list")
    @GetMapping
    public Result<PageResult<SupplierScoreVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String grade) {
        return Result.ok(scoreService.page(pageNum, pageSize, supplierId, periodType, period, grade));
    }

    /** 汇总报表（等级分布/均值/交易额） */
    @Operation(summary = "评级汇总报表")
    @RequirePerm("purchase:supplierScore:list")
    @GetMapping("/summary")
    public Result<SupplierGradeSummaryVO> summary(
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) String period) {
        return Result.ok(scoreService.summary(periodType, period));
    }

    /** 手工重算（period 空=最近一个已完结周期） */
    @Operation(summary = "手工重算评分")
    @RequirePerm("purchase:supplierScore:recalc")
    @OperLog(module = "供应商评级", operation = "手工重算评分")
    @Idempotent
    @PostMapping("/recalc")
    public Result<Integer> recalc(
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) String period) {
        return Result.ok(scoreService.recalc(periodType, period));
    }
}
