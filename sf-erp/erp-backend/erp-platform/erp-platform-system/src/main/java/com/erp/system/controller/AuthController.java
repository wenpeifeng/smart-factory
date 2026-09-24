package com.erp.system.controller;

import com.erp.common.result.Result;
import com.erp.system.annotation.OperLog;
import com.erp.system.dto.auth.LoginForm;
import com.erp.system.dto.auth.LoginVO;
import com.erp.system.dto.auth.UserContextVO;
import com.erp.system.dto.menu.MenuVO;
import com.erp.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口：登录 / 登出 / 当前用户 / 当前菜单。
 * <p>
 * /auth/login 在 SecurityConfig 白名单内；其余接口需携带
 * {@code Authorization: Bearer {token}}。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录（账号密码 + 可选租户 → JWT + 用户信息 + 租户/组织上下文） */
    @Operation(summary = "登录")
    @OperLog(module = "认证管理", operation = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginForm form) {
        return Result.ok(authService.login(form));
    }

    /** 切换当前组织（重签携带新 orgId 的令牌，数据随上下文自动过滤） */
    @Operation(summary = "切换组织")
    @OperLog(module = "认证管理", operation = "切换组织")
    @GetMapping("/switch-org/{orgId}")
    public Result<LoginVO> switchOrg(@PathVariable Long orgId) {
        return Result.ok(authService.switchOrg(orgId));
    }

    /** 登出（Redis 删除在线会话并拉黑令牌） */
    @Operation(summary = "登出")
    @OperLog(module = "认证管理", operation = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                               HttpServletRequest request) {
        // 直接从原始头取令牌（登出时令牌即将失效，不走 Provider 的综合校验）
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7).trim() : null;
        authService.logout(token);
        return Result.ok();
    }

    /** 当前登录用户上下文（用户信息 + 角色 + 按钮权限） */
    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<UserContextVO> me() {
        return Result.ok(authService.getCurrentUser());
    }

    /** 当前用户可见菜单树（目录+菜单，前端路由/侧边栏数据源） */
    @Operation(summary = "当前用户菜单树")
    @GetMapping("/menus")
    public Result<List<MenuVO>> menus() {
        return Result.ok(authService.getCurrentMenus());
    }
}
