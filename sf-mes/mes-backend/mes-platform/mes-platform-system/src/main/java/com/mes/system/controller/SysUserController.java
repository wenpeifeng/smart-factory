package com.mes.system.controller;

import com.mes.common.exception.BusinessException;
import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.system.annotation.OperLog;
import com.mes.system.dto.user.UserOrgForm;
import com.mes.system.dto.user.UserQuery;
import com.mes.system.dto.user.UserRoleForm;
import com.mes.system.dto.user.UserVO;
import com.mes.system.service.SysUserService;
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
 * 用户管理接口（只读 + 分配角色）。
 * <p>主账号生命周期（建号/改密/离职/档案变更）由 auth-center 统一管控，本控制器不提供
 * 任何写档案接口；启停走应用级（PUT /integration/auth/users/{employeeNo}/status，
 * 仅影响本系统登录，不动该用户在其他系统的权限）。
 * <p>
 * 出参一律 {@link UserVO}（永不包含 password 字段）；路径 id 为准，覆盖表单内 id。
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
