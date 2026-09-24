package com.mes.system.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.system.annotation.OperLog;
import com.mes.system.dto.Create;
import com.mes.system.dto.StatusForm;
import com.mes.system.dto.Update;
import com.mes.system.dto.tenant.TenantForm;
import com.mes.system.dto.tenant.TenantQuery;
import com.mes.system.dto.tenant.TenantVO;
import com.mes.system.service.SysTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.mes.framework.security.RequirePerm;

/**
 * 租户管理接口（管理门户 /system/tenants）。
 * <p>
 * sys_tenant 在租户插件白名单内，平台管理员跨租户维护；
 * 默认租户 1000 受服务层保护不可停用/改码。
 */
@Tag(name = "租户管理")
@RestController
@RequestMapping("/system/tenants")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService tenantService;

    /** 分页查询租户 */
    @Operation(summary = "分页查询租户")
    @RequirePerm("system:tenant:list")
    @GetMapping
    public Result<PageResult<TenantVO>> page(@ModelAttribute TenantQuery query) {
        return Result.ok(tenantService.page(query));
    }

    /** 启用租户列表（登录页租户选择器） */
    @Operation(summary = "启用租户列表")
    @GetMapping("/enabled")
    public Result<List<TenantVO>> listEnabled() {
        return Result.ok(tenantService.listEnabled());
    }

    /** 租户详情 */
    @Operation(summary = "租户详情")
    @RequirePerm("system:tenant:list")
    @GetMapping("/{id}")
    public Result<TenantVO> detail(@PathVariable Long id) {
        return Result.ok(tenantService.getDetail(id));
    }

    /** 新增租户 */
    @Operation(summary = "新增租户")
    @OperLog(module = "租户管理", operation = "新增租户")
    @RequirePerm("system:tenant:add")
    @PostMapping
    public Result<Long> create(@Validated(Create.class) @RequestBody TenantForm form) {
        return Result.ok(tenantService.create(form));
    }

    /** 修改租户 */
    @Operation(summary = "修改租户")
    @OperLog(module = "租户管理", operation = "修改租户")
    @RequirePerm("system:tenant:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(Update.class) @RequestBody TenantForm form) {
        form.setId(id);
        tenantService.update(form);
        return Result.ok();
    }

    /** 启停租户（默认租户 1000 不允许停用） */
    @Operation(summary = "启停租户")
    @OperLog(module = "租户管理", operation = "启停租户")
    @RequirePerm("system:tenant:edit")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Validated @RequestBody StatusForm form) {
        tenantService.updateStatus(id, form.getStatus());
        return Result.ok();
    }
}
