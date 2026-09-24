package com.erp.system.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.system.annotation.OperLog;
import com.erp.system.dto.Create;
import com.erp.system.dto.Update;
import com.erp.system.dto.dict.DictTypeForm;
import com.erp.system.dto.dict.DictTypeQuery;
import com.erp.system.dto.dict.DictTypeVO;
import com.erp.system.service.SysDictTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 字典类型管理接口。
 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/system/dict/types")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final SysDictTypeService dictTypeService;

    /** 分页查询字典类型 */
    @Operation(summary = "分页查询字典类型")
    @RequirePerm("system:dict:list")
    @GetMapping
    public Result<PageResult<DictTypeVO>> page(@ModelAttribute DictTypeQuery query) {
        return Result.ok(dictTypeService.page(query));
    }

    /** 新增字典类型 */
    @Operation(summary = "新增字典类型")
    @OperLog(module = "字典管理", operation = "新增字典类型")
    @RequirePerm("system:dict:add")
    @PostMapping
    public Result<Long> create(@Validated(Create.class) @RequestBody DictTypeForm form) {
        return Result.ok(dictTypeService.create(form));
    }

    /** 修改字典类型（编码变更级联更新字典项） */
    @Operation(summary = "修改字典类型")
    @OperLog(module = "字典管理", operation = "修改字典类型")
    @RequirePerm("system:dict:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(Update.class) @RequestBody DictTypeForm form) {
        form.setId(id);
        dictTypeService.update(form);
        return Result.ok();
    }

    /** 删除字典类型（存在字典项时拒绝） */
    @Operation(summary = "删除字典类型")
    @OperLog(module = "字典管理", operation = "删除字典类型")
    @RequirePerm("system:dict:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return Result.ok();
    }
}
