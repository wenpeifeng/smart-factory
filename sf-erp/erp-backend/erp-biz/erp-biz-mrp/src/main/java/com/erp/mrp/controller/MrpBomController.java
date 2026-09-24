package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.BomCopyForm;
import com.erp.mrp.dto.form.BomForm;
import com.erp.mrp.dto.vo.BomTreeVO;
import com.erp.mrp.dto.vo.BomVO;
import com.erp.mrp.service.MrpBomService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * BOM 物料清单接口：CRUD、复制新版本、审核/停用、多级树形展开。
 */
@Tag(name = "生产制造-BOM")
@RestController
@RequestMapping("/mrp/boms")
@RequiredArgsConstructor
public class MrpBomController {

    private final MrpBomService bomService;

    /** 分页查询 BOM */
    @Operation(summary = "分页查询BOM")
    @RequirePerm("mrp:bom:list")
    @GetMapping
    public Result<PageResult<BomVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(bomService.page(pageNum, pageSize, keyword, status));
    }

    /** BOM 详情（含子件明细） */
    @Operation(summary = "BOM详情")
    @RequirePerm("mrp:bom:list")
    @GetMapping("/{id}")
    public Result<BomVO> detail(@PathVariable Long id) {
        return Result.ok(bomService.detail(id));
    }

    /** 新建 BOM（草稿） */
    @Operation(summary = "新建BOM")
    @RequirePerm("mrp:bom:add")
    @OperLog(module = "生产制造", operation = "新增BOM")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BomForm form) {
        return Result.ok(bomService.create(form));
    }

    /** 修改 BOM（仅草稿） */
    @Operation(summary = "修改BOM")
    @RequirePerm("mrp:bom:edit")
    @OperLog(module = "生产制造", operation = "修改BOM")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BomForm form) {
        bomService.update(id, form);
        return Result.ok();
    }

    /** 删除 BOM（逻辑删除） */
    @Operation(summary = "删除BOM")
    @RequirePerm("mrp:bom:delete")
    @OperLog(module = "生产制造", operation = "删除BOM")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bomService.delete(id);
        return Result.ok();
    }

    /** 复制为新版本 */
    @Operation(summary = "复制BOM新版本")
    @RequirePerm("mrp:bom:add")
    @OperLog(module = "生产制造", operation = "复制BOM")
    @Idempotent
    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable Long id, @Valid @RequestBody BomCopyForm form) {
        return Result.ok(bomService.copy(id, form));
    }

    /** 审核 BOM（草稿→有效） */
    @Operation(summary = "审核BOM")
    @RequirePerm("mrp:bom:audit")
    @OperLog(module = "生产制造", operation = "审核BOM")
    @Idempotent
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        bomService.audit(id);
        return Result.ok();
    }

    /** 停用 BOM（有效→停用） */
    @Operation(summary = "停用BOM")
    @RequirePerm("mrp:bom:audit")
    @OperLog(module = "生产制造", operation = "停用BOM")
    @Idempotent
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        bomService.disable(id);
        return Result.ok();
    }

    /** 多级树形展开（深度上限15，循环引用抛异常） */
    @Operation(summary = "BOM树形展开")
    @RequirePerm("mrp:bom:list")
    @GetMapping("/{id}/tree")
    public Result<BomTreeVO> tree(@PathVariable Long id) {
        return Result.ok(bomService.tree(id));
    }
}
