package com.erp.mrp.controller;

import com.erp.common.result.Result;
import com.erp.mrp.dto.form.BomSubstituteBatchForm;
import com.erp.mrp.dto.vo.BomSubstituteVO;
import com.erp.mrp.service.MrpBomSubstituteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * BOM 子件替代组接口（V621）：按子件行查询 / 整组保存 / 单条删除。
 * 仅 DRAFT 状态 BOM 允许维护；倒冲领料链路消费替代组。
 */
@Tag(name = "生产制造-BOM替代料")
@RestController
@RequestMapping("/mrp/bom-substitutes")
@RequiredArgsConstructor
public class MrpBomSubstituteController {

    private final MrpBomSubstituteService substituteService;

    /** 某子件行的替代料列表（优先级升序） */
    @Operation(summary = "子件替代料列表")
    @RequirePerm("mrp:bom:list")
    @GetMapping(params = "bomItemId")
    public Result<List<BomSubstituteVO>> listByItem(@RequestParam Long bomItemId) {
        return Result.ok(substituteService.listByItem(bomItemId));
    }

    /** 整个 BOM 的替代组（bomItemId → 列表，BOM 维护界面一次拉全） */
    @Operation(summary = "BOM全部替代组")
    @RequirePerm("mrp:bom:list")
    @GetMapping(params = "bomId")
    public Result<Map<Long, List<BomSubstituteVO>>> listByBom(@RequestParam Long bomId) {
        return Result.ok(substituteService.listByBom(bomId));
    }

    /** 按子件行整组保存替代料（先删后插） */
    @Operation(summary = "整组保存替代料")
    @RequirePerm("mrp:bom:substitute")
    @OperLog(module = "生产制造", operation = "维护BOM替代料")
    @Idempotent
    @PostMapping("/items/{bomItemId}")
    public Result<Void> saveBatch(@PathVariable Long bomItemId,
                                  @Valid @RequestBody BomSubstituteBatchForm form) {
        substituteService.saveBatch(bomItemId, form);
        return Result.ok();
    }

    /** 删除单条替代料 */
    @Operation(summary = "删除替代料")
    @RequirePerm("mrp:bom:substitute")
    @OperLog(module = "生产制造", operation = "删除BOM替代料")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        substituteService.delete(id);
        return Result.ok();
    }
}
