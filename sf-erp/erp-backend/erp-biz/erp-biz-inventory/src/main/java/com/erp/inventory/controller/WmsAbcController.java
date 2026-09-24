package com.erp.inventory.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.AbcClassVO;
import com.erp.inventory.service.WmsAbcService;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * WMS ABC 分级接口（V421）：现行分级 / 手动重算 / 分级计数。
 */
@Tag(name = "库存管理-WMS循环盘点(ABC)")
@RestController
@RequestMapping("/inventory/wms/abc")
@RequiredArgsConstructor
public class WmsAbcController {

    private final WmsAbcService abcService;

    /** 现行分级分页（按出库频次降序） */
    @Operation(summary = "现行ABC分级")
    @RequirePerm("inventory:wms:abc:list")
    @GetMapping
    public Result<PageResult<AbcClassVO>> page(WmsTaskQuery query) {
        return Result.ok(abcService.page(query));
    }

    /** 各分级物料数（KPI 卡片） */
    @Operation(summary = "ABC分级计数")
    @RequirePerm("inventory:wms:abc:list")
    @GetMapping("/counts")
    public Result<List<Map<String, Object>>> counts() {
        return Result.ok(abcService.levelCounts());
    }

    /** 手动重算分级（近 90 天出库频次，前 20% A / 30% B / 50% C） */
    @Operation(summary = "手动重算ABC分级")
    @RequirePerm("inventory:wms:abc:edit")
    @OperLog(module = "WMS循环盘点", operation = "重算ABC分级")
    @Idempotent
    @PostMapping("/recalc")
    public Result<List<Map<String, Object>>> recalc() {
        return Result.ok(abcService.recalc());
    }

    /** 手动触发一轮循环盘点生成（平时由每月 1 号定时器驱动） */
    @Operation(summary = "手动触发循环盘点生成")
    @RequirePerm("inventory:wms:abc:audit")
    @OperLog(module = "WMS循环盘点", operation = "生成循环盘点")
    @Idempotent
    @PostMapping("/cycle-checks")
    public Result<Map<String, Integer>> cycleChecks() {
        return Result.ok(Map.of("created", abcService.generateCycleChecks()));
    }
}
