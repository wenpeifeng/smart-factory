package com.erp.mrp.controller;

import com.erp.common.result.Result;
import com.erp.mrp.dto.vo.CapacityLoadResultVO;
import com.erp.mrp.service.MrpCapacityService;
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
 * 能力计划(CRP 简版)接口 —— 期间各工作中心负荷率与超载列表。
 */
@Tag(name = "生产制造-能力计划")
@RestController
@RequestMapping("/mrp/capacity")
@RequiredArgsConstructor
public class MrpCapacityController {

    private final MrpCapacityService capacityService;

    /** 能力负荷查询 */
    @Operation(summary = "工作中心能力负荷")
    @RequirePerm("mrp:capacity:list")
    @GetMapping("/load")
    public Result<CapacityLoadResultVO> load(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long workCenterId) {
        return Result.ok(capacityService.load(from, to, workCenterId));
    }
}
