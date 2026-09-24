package com.mes.basedata.controller;

import com.mes.basedata.dto.form.BaseCustomerForm;
import com.mes.basedata.dto.vo.BaseCustomerVO;
import com.mes.basedata.service.BaseCustomerService;
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
 * 客户接口。
 */
@Tag(name = "基础资料-客户")
@RestController
@RequestMapping("/basedata/customers")
@RequiredArgsConstructor
public class BaseCustomerController {

    private final BaseCustomerService customerService;

    /** 分页查询客户 */
    @Operation(summary = "分页查询客户")
    @RequirePerm("basedata:customer:list")
    @GetMapping
    public Result<PageResult<BaseCustomerVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(customerService.page(pageNum, pageSize, keyword));
    }

    /** 启用客户搜索（单据选择器下拉） */
    @Operation(summary = "启用客户搜索(下拉)")
    @GetMapping("/enabled")
    public Result<List<BaseCustomerVO>> listEnabled(@RequestParam(required = false) String keyword) {
        return Result.ok(customerService.listEnabled(keyword));
    }

    /** 客户详情 */
    @Operation(summary = "客户详情")
    @RequirePerm("basedata:customer:list")
    @GetMapping("/{id}")
    public Result<BaseCustomerVO> getById(@PathVariable Long id) {
        return Result.ok(customerService.getById(id));
    }

    /** 新增客户（编码留空自动生成） */
    @Operation(summary = "新增客户")
    @RequirePerm("basedata:customer:add")
    @OperLog(module = "客户管理", operation = "新增客户")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BaseCustomerForm form) {
        return Result.ok(customerService.create(form));
    }

    /** 修改客户 */
    @Operation(summary = "修改客户")
    @RequirePerm("basedata:customer:edit")
    @OperLog(module = "客户管理", operation = "修改客户")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseCustomerForm form) {
        customerService.update(id, form);
        return Result.ok();
    }

    /** 删除客户 */
    @Operation(summary = "删除客户")
    @RequirePerm("basedata:customer:delete")
    @OperLog(module = "客户管理", operation = "删除客户")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.ok();
    }
}
