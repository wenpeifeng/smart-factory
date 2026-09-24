package com.erp.system.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.system.annotation.OperLog;
import com.erp.system.dto.user.UserOrgForm;
import com.erp.system.dto.user.UserQuery;
import com.erp.system.dto.user.UserRoleForm;
import com.erp.system.dto.user.UserScopeForm;
import com.erp.system.dto.user.UserVO;
import com.erp.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 用户管理接口（身份体系已移交统一认证中心，与 MES 同款收敛口径）。
 * <p>
 * 用户主档案（建号/改密/离职）只在认证中心操作，本系统<b>不再提供</b>
 * 新增/修改/删除/重置密码；保留：查询 / 分配角色（本系统内部授权）/
 * 绑定组织 / 数据范围（五档数据权限属本系统授权）。
 * 用户来源：认证中心同步（每小时对账 + 手动触发，见 /auth/sso/sync）+
 * SSO 首登 JIT 建档；账号启停走应用级启停（/auth/sso/users/{id}/app-status，
 * 调认证中心仅影响本系统）。
 * <p>
 * 出参一律 {@link UserVO}（永不包含 password 字段）。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    /** 分页查询用户（关键词/部门/状态过滤） */
    @Operation(summary = "分页查询用户")
    @RequirePerm("system:user:list")
    @GetMapping
    public Result<PageResult<UserVO>> page(@ModelAttribute UserQuery query) {
        return Result.ok(userService.page(query));
    }

    /** 用户详情（含角色回显） */
    @Operation(summary = "用户详情")
    @RequirePerm("system:user:list")
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.getDetail(id));
    }

    /** 数据范围/部门/组织调整（五档数据权限，本系统内部授权；身份字段不可改） */
    @Operation(summary = "调整数据范围与归属")
    @OperLog(module = "用户管理", operation = "调整数据范围")
    @RequirePerm("system:user:edit")
    @PutMapping("/{id}/scope")
    public Result<Void> updateScope(@PathVariable Long id,
                                    @Valid @RequestBody UserScopeForm form) {
        userService.updateScope(id, form);
        return Result.ok();
    }

    /** 分配角色（整体覆盖） */
    @Operation(summary = "分配角色")
    @OperLog(module = "用户管理", operation = "分配角色")
    @RequirePerm("system:user:edit")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @Valid @RequestBody UserRoleForm form) {
        userService.assignRoles(id, form.getRoleIds());
        return Result.ok();
    }

    /** 用户已绑定的组织（空列表 = 不限制，登录可见全部启用组织） */
    @Operation(summary = "用户已绑定组织")
    @RequirePerm("system:user:list")
    @GetMapping("/{id}/orgs")
    public Result<List<Long>> listOrgs(@PathVariable Long id) {
        return Result.ok(userService.getUserOrgIds(id));
    }

    /** 绑定组织（整体覆盖；登录可切换组织列表收敛为绑定 ∩ 启用，重新登录后生效） */
    @Operation(summary = "绑定组织")
    @OperLog(module = "用户管理", operation = "绑定组织")
    @RequirePerm("system:user:edit")
    @PutMapping("/{id}/orgs")
    public Result<Void> assignOrgs(@PathVariable Long id,
                                   @Valid @RequestBody UserOrgForm form) {
        userService.assignOrgs(id, form.getOrgIds());
        return Result.ok();
    }
}
