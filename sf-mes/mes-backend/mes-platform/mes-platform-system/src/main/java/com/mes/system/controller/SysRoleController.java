package com.mes.system.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.system.annotation.OperLog;
import com.mes.system.dto.Create;
import com.mes.system.dto.Update;
import com.mes.system.dto.role.RoleForm;
import com.mes.system.dto.role.RoleMenuForm;
import com.mes.system.dto.role.RoleQuery;
import com.mes.system.dto.role.RoleVO;
import com.mes.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import com.mes.framework.security.RequirePerm;

/**
 * 角色管理接口（含按角色分配菜单）。
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    /** 分页查询角色 */
    @Operation(summary = "分页查询角色")
    @RequirePerm("system:role:list")
    @GetMapping
    public Result<PageResult<RoleVO>> page(@ModelAttribute RoleQuery query) {
        return Result.ok(roleService.page(query));
    }

    /** 全量角色列表（用户分配角色下拉数据） */
    @Operation(summary = "角色列表(全量)")
    @RequirePerm("system:role:list")
    @GetMapping("/all")
    public Result<List<RoleVO>> listAll() {
        return Result.ok(roleService.listAll());
    }

    /** 角色详情 */
    @Operation(summary = "角色详情")
    @RequirePerm("system:role:list")
    @GetMapping("/{id}")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.getDetail(id));
    }

    /** 新增角色 */
    @Operation(summary = "新增角色")
    @OperLog(module = "角色管理", operation = "新增角色")
    @RequirePerm("system:role:add")
    @PostMapping
    public Result<Long> create(@Validated(Create.class) @RequestBody RoleForm form) {
        return Result.ok(roleService.create(form));
    }

    /** 修改角色 */
    @Operation(summary = "修改角色")
    @OperLog(module = "角色管理", operation = "修改角色")
    @RequirePerm("system:role:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(Update.class) @RequestBody RoleForm form) {
        form.setId(id);
        roleService.update(form);
        return Result.ok();
    }

    /** 删除角色（内置超管不可删） */
    @Operation(summary = "删除角色")
    @OperLog(module = "角色管理", operation = "删除角色")
    @RequirePerm("system:role:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    /** 角色已分配的菜单 ID 列表（分配弹窗回显） */
    @Operation(summary = "角色菜单ID列表")
    @RequirePerm("system:role:list")
    @GetMapping("/{id}/menus")
    public Result<List<Long>> getMenuIds(@PathVariable Long id) {
        return Result.ok(roleService.getMenuIds(id));
    }

    /** 为角色分配菜单（整体覆盖，含按钮） */
    @Operation(summary = "分配菜单")
    @OperLog(module = "角色管理", operation = "分配菜单")
    @RequirePerm("system:role:edit")
    @PutMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id,
                                    @Valid @RequestBody RoleMenuForm form) {
        roleService.assignMenus(id, form.getMenuIds());
        return Result.ok();
    }
}
