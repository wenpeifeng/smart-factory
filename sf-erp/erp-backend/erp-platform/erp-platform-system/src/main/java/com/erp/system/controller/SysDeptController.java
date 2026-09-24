package com.erp.system.controller;

import com.erp.common.result.Result;
import com.erp.system.annotation.OperLog;
import com.erp.system.dto.Create;
import com.erp.system.dto.Update;
import com.erp.system.dto.dept.DeptForm;
import com.erp.system.dto.dept.DeptVO;
import com.erp.system.service.SysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
import com.erp.framework.security.RequirePerm;

/**
 * 部门管理接口（树形）。
 */
@Tag(name = "部门管理")
@RestController
@RequestMapping("/system/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    /** 部门树 */
    @Operation(summary = "部门树")
    @RequirePerm("system:dept:list")
    @GetMapping
    public Result<List<DeptVO>> tree(@RequestParam(required = false) String keyword) {
        return Result.ok(deptService.tree(keyword));
    }

    /** 部门详情 */
    @Operation(summary = "部门详情")
    @RequirePerm("system:dept:list")
    @GetMapping("/{id}")
    public Result<DeptVO> detail(@PathVariable Long id) {
        return Result.ok(deptService.getDetail(id));
    }

    /** 新增部门 */
    @Operation(summary = "新增部门")
    @OperLog(module = "部门管理", operation = "新增部门")
    @RequirePerm("system:dept:add")
    @PostMapping
    public Result<Long> create(@Validated(Create.class) @RequestBody DeptForm form) {
        return Result.ok(deptService.create(form));
    }

    /** 修改部门 */
    @Operation(summary = "修改部门")
    @OperLog(module = "部门管理", operation = "修改部门")
    @RequirePerm("system:dept:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(Update.class) @RequestBody DeptForm form) {
        form.setId(id);
        deptService.update(form);
        return Result.ok();
    }

    /** 删除部门 */
    @Operation(summary = "删除部门")
    @OperLog(module = "部门管理", operation = "删除部门")
    @RequirePerm("system:dept:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}
