package com.erp.basedata.controller;

import com.erp.basedata.dto.form.BaseMaterialCategoryForm;
import com.erp.basedata.dto.vo.BaseMaterialCategoryVO;
import com.erp.basedata.service.BaseMaterialCategoryService;
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
 * 物料分类接口（树形 CRUD）。
 */
@Tag(name = "基础资料-物料分类")
@RestController
@RequestMapping("/basedata/material-categories")
@RequiredArgsConstructor
public class BaseMaterialCategoryController {

    private final BaseMaterialCategoryService categoryService;

    /** 分类树 */
    @Operation(summary = "分类树查询")
    @RequirePerm("basedata:material:list")
    @GetMapping("/tree")
    public Result<List<BaseMaterialCategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    /** 分类详情 */
    @Operation(summary = "分类详情")
    @RequirePerm("basedata:material:list")
    @GetMapping("/{id}")
    public Result<BaseMaterialCategoryVO> getById(@PathVariable Long id) {
        return Result.ok(BaseMaterialCategoryVO.from(categoryService.getById(id)));
    }

    /** 新增分类 */
    @Operation(summary = "新增分类")
    @RequirePerm("basedata:material:add")
    @OperLog(module = "物料管理", operation = "新增物料分类")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BaseMaterialCategoryForm form) {
        return Result.ok(categoryService.create(form));
    }

    /** 修改分类 */
    @Operation(summary = "修改分类")
    @RequirePerm("basedata:material:edit")
    @OperLog(module = "物料管理", operation = "修改物料分类")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseMaterialCategoryForm form) {
        categoryService.update(id, form);
        return Result.ok();
    }

    /** 删除分类（有子分类或被物料引用抛 DATA_IN_USE） */
    @Operation(summary = "删除分类")
    @RequirePerm("basedata:material:delete")
    @OperLog(module = "物料管理", operation = "删除物料分类")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
