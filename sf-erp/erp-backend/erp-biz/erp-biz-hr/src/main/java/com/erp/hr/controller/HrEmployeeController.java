package com.erp.hr.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.hr.dto.form.EmployeeForm;
import com.erp.hr.dto.form.ResignForm;
import com.erp.hr.dto.vo.EmployeeVO;
import com.erp.hr.service.HrEmployeeService;
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
 * 员工档案接口：CRUD、分页（部门/状态/姓名）、离职办理。
 * <p>
 * 出参身份证号一律脱敏（前6后4）。
 */
@Tag(name = "人力资源-员工档案")
@RestController
@RequestMapping("/hr/employees")
@RequiredArgsConstructor
public class HrEmployeeController {

    private final HrEmployeeService employeeService;

    /** 分页查询员工（姓名模糊/部门/状态） */
    @Operation(summary = "分页查询员工")
    @RequirePerm("hr:employee:list")
    @GetMapping
    public Result<PageResult<EmployeeVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String status) {
        return Result.ok(employeeService.page(pageNum, pageSize, keyword, deptId, status));
    }

    /** 员工详情（身份证脱敏） */
    @Operation(summary = "员工详情")
    @RequirePerm("hr:employee:list")
    @GetMapping("/{id}")
    public Result<EmployeeVO> detail(@PathVariable Long id) {
        return Result.ok(employeeService.detail(id));
    }

    /** 新增员工（工号自动生成） */
    @Operation(summary = "新增员工")
    @RequirePerm("hr:employee:add")
    @OperLog(module = "人力资源", operation = "新增员工")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody EmployeeForm form) {
        return Result.ok(employeeService.create(form));
    }

    /** 修改员工（状态/离职日期走离职办理接口） */
    @Operation(summary = "修改员工")
    @RequirePerm("hr:employee:edit")
    @OperLog(module = "人力资源", operation = "修改员工")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmployeeForm form) {
        employeeService.update(id, form);
        return Result.ok();
    }

    /** 删除员工（逻辑删除） */
    @Operation(summary = "删除员工")
    @RequirePerm("hr:employee:delete")
    @OperLog(module = "人力资源", operation = "删除员工")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return Result.ok();
    }

    /** 离职办理：填离职日期 + 状态置离职（单向） */
    @Operation(summary = "离职办理")
    @RequirePerm("hr:employee:edit")
    @OperLog(module = "人力资源", operation = "员工离职")
    @Idempotent
    @PutMapping("/{id}/resign")
    public Result<Void> resign(@PathVariable Long id, @Valid @RequestBody ResignForm form) {
        employeeService.resign(id, form);
        return Result.ok();
    }
}
