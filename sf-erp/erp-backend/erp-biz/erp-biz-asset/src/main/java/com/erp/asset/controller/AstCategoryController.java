package com.erp.asset.controller;

import com.erp.asset.dto.form.CategoryForm;
import com.erp.asset.dto.vo.CategoryVO;
import com.erp.asset.service.AstCategoryService;
import com.erp.common.result.Result;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 资产类别管理接口（折旧政策 + 科目映射）。
 * <p>
 * 类别量级小，列表接口不分页，全量返回。
 */
@Tag(name = "资产类别")
@RestController
@RequestMapping("/asset/categories")
@RequiredArgsConstructor
public class AstCategoryController {

    private final AstCategoryService categoryService;

    /** 类别全量列表（含科目映射回显） */
    @Operation(summary = "类别列表")
    @RequirePerm("asset:category:list")
    @GetMapping
    public Result<List<CategoryVO>> list() {
        return Result.ok(categoryService.list());
    }

    /** 新增类别 */
    @Operation(summary = "新增类别")
    @RequirePerm("asset:category:add")
    @OperLog(module = "资产管理", operation = "新增资产类别")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CategoryForm form) {
        return Result.ok(categoryService.create(form));
    }

    /** 修改类别 */
    @Operation(summary = "修改类别")
    @RequirePerm("asset:category:edit")
    @OperLog(module = "资产管理", operation = "修改资产类别")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        categoryService.update(id, form);
        return Result.ok();
    }

    /** 删除类别（有资产引用时拒绝） */
    @Operation(summary = "删除类别")
    @RequirePerm("asset:category:delete")
    @OperLog(module = "资产管理", operation = "删除资产类别")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
