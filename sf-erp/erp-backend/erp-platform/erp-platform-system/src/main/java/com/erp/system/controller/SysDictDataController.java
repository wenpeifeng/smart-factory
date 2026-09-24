package com.erp.system.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.system.annotation.OperLog;
import com.erp.system.dto.Create;
import com.erp.system.dto.Update;
import com.erp.system.dto.dict.DictDataForm;
import com.erp.system.dto.dict.DictDataQuery;
import com.erp.system.dto.dict.DictDataVO;
import com.erp.system.service.SysDictDataService;
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
 * 字典数据（字典项）管理接口。
 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/system/dict/datas")
@RequiredArgsConstructor
public class SysDictDataController {

    private final SysDictDataService dictDataService;

    /** 分页查询字典项（按类型过滤） */
    @Operation(summary = "分页查询字典项")
    @RequirePerm("system:dict:list")
    @GetMapping
    public Result<PageResult<DictDataVO>> page(@ModelAttribute DictDataQuery query) {
        return Result.ok(dictDataService.page(query));
    }

    /** 新增字典项 */
    @Operation(summary = "新增字典项")
    @OperLog(module = "字典管理", operation = "新增字典项")
    @RequirePerm("system:dict:add")
    @PostMapping
    public Result<Long> create(@Validated(Create.class) @RequestBody DictDataForm form) {
        return Result.ok(dictDataService.create(form));
    }

    /** 修改字典项 */
    @Operation(summary = "修改字典项")
    @OperLog(module = "字典管理", operation = "修改字典项")
    @RequirePerm("system:dict:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(Update.class) @RequestBody DictDataForm form) {
        form.setId(id);
        dictDataService.update(form);
        return Result.ok();
    }

    /** 删除字典项 */
    @Operation(summary = "删除字典项")
    @OperLog(module = "字典管理", operation = "删除字典项")
    @RequirePerm("system:dict:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictDataService.delete(id);
        return Result.ok();
    }
}
