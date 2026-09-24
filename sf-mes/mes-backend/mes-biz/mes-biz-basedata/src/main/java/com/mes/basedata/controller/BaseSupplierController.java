package com.mes.basedata.controller;

import com.mes.basedata.dto.form.BaseSupplierForm;
import com.mes.basedata.dto.vo.BaseSupplierVO;
import com.mes.basedata.service.BaseSupplierService;
import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
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

import java.util.List;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;

/**
 * 供应商接口。
 */
@Tag(name = "基础资料-供应商")
@RestController
@RequestMapping("/basedata/suppliers")
@RequiredArgsConstructor
public class BaseSupplierController {

    private final BaseSupplierService supplierService;

    /** 分页查询供应商 */
    @Operation(summary = "分页查询供应商")
    @RequirePerm("basedata:supplier:list")
    @GetMapping
    public Result<PageResult<BaseSupplierVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(supplierService.page(pageNum, pageSize, keyword));
    }

    /** 启用供应商搜索（单据选择器下拉） */
    @Operation(summary = "启用供应商搜索(下拉)")
    @GetMapping("/enabled")
    public Result<List<BaseSupplierVO>> listEnabled(@RequestParam(required = false) String keyword) {
        return Result.ok(supplierService.listEnabled(keyword));
    }

    /** 供应商详情 */
    @Operation(summary = "供应商详情")
    @RequirePerm("basedata:supplier:list")
    @GetMapping("/{id}")
    public Result<BaseSupplierVO> getById(@PathVariable Long id) {
        return Result.ok(supplierService.getById(id));
    }

    /** 新增供应商（编码留空自动生成） */
    @Operation(summary = "新增供应商")
    @RequirePerm("basedata:supplier:add")
    @OperLog(module = "供应商管理", operation = "新增供应商")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BaseSupplierForm form) {
        return Result.ok(supplierService.create(form));
    }

    /** 修改供应商 */
    @Operation(summary = "修改供应商")
    @RequirePerm("basedata:supplier:edit")
    @OperLog(module = "供应商管理", operation = "修改供应商")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseSupplierForm form) {
        supplierService.update(id, form);
        return Result.ok();
    }

    /** 删除供应商 */
    @Operation(summary = "删除供应商")
    @RequirePerm("basedata:supplier:delete")
    @OperLog(module = "供应商管理", operation = "删除供应商")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok();
    }
}
