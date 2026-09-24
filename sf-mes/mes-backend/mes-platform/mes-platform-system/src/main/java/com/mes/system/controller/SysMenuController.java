package com.mes.system.controller;

import com.mes.common.result.Result;
import com.mes.system.annotation.OperLog;
import com.mes.system.dto.Create;
import com.mes.system.dto.Update;
import com.mes.system.dto.menu.MenuForm;
import com.mes.system.dto.menu.MenuVO;
import com.mes.system.service.SysMenuService;
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
import com.mes.framework.security.RequirePerm;

/**
 * 菜单管理接口（目录/菜单/按钮三级树）。
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    /** 菜单树（管理页全量树，关键词过滤时保留祖先链） */
    @Operation(summary = "菜单树")
    @RequirePerm("system:menu:list")
    @GetMapping
    public Result<List<MenuVO>> tree(@RequestParam(required = false) String keyword) {
        return Result.ok(menuService.tree(keyword));
    }

    /** 菜单详情 */
    @Operation(summary = "菜单详情")
    @RequirePerm("system:menu:list")
    @GetMapping("/{id}")
    public Result<MenuVO> detail(@PathVariable Long id) {
        return Result.ok(menuService.getDetail(id));
    }

    /** 新增菜单 */
    @Operation(summary = "新增菜单")
    @OperLog(module = "菜单管理", operation = "新增菜单")
    @RequirePerm("system:menu:add")
    @PostMapping
    public Result<Long> create(@Validated(Create.class) @RequestBody MenuForm form) {
        return Result.ok(menuService.create(form));
    }

    /** 修改菜单 */
    @Operation(summary = "修改菜单")
    @OperLog(module = "菜单管理", operation = "修改菜单")
    @RequirePerm("system:menu:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(Update.class) @RequestBody MenuForm form) {
        form.setId(id);
        menuService.update(form);
        return Result.ok();
    }

    /** 删除菜单（存在下级或被引用时拒绝） */
    @Operation(summary = "删除菜单")
    @OperLog(module = "菜单管理", operation = "删除菜单")
    @RequirePerm("system:menu:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.ok();
    }
}
