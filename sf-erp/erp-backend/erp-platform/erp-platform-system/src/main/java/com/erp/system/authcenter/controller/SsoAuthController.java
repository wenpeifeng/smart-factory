package com.erp.system.authcenter.controller;

import com.erp.common.result.Result;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import com.erp.system.authcenter.dto.SsoCallbackForm;
import com.erp.system.authcenter.dto.UserSyncResultVO;
import com.erp.system.authcenter.oidc.OidcAuthorizeVO;
import com.erp.system.authcenter.service.AuthUserSyncService;
import com.erp.system.authcenter.service.SsoLoginService;
import com.erp.system.dto.auth.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一认证中心 SSO 接口（/auth/sso/**）。
 * <p>authorize / callback 须匿名可达（授权码由 state 一次性校验保护，
 * SecurityConfig 白名单变更见 docs/modules/sso-authcenter.md）；
 * sync 为管理端手动触发，需 system:user:edit 权限码。
 */
@Tag(name = "认证-统一登录SSO")
@RestController
@RequestMapping("/auth/sso")
@RequiredArgsConstructor
public class SsoAuthController {

    private final SsoLoginService ssoLoginService;
    private final AuthUserSyncService userSyncService;

    /** 发起 SSO：生成 state+PKCE verifier（Redis 5 分钟），返回统一登录页跳转 URL */
    @Operation(summary = "获取统一登录跳转URL")
    @GetMapping("/authorize")
    public Result<OidcAuthorizeVO> authorize() {
        return Result.ok(ssoLoginService.buildAuthorizeUrl());
    }

    /** SSO 回调：授权码换令牌 + JWKS 验签 + employee_no 匹配本地用户 → 签发本地 ERP JWT */
    @Operation(summary = "SSO授权码登录")
    @PostMapping("/callback")
    public Result<LoginVO> callback(@Valid @RequestBody SsoCallbackForm form) {
        return Result.ok(ssoLoginService.loginByCode(form));
    }

    /** 手动触发一次认证中心用户同步（定时任务之外的补偿入口） */
    @Operation(summary = "手动同步认证中心用户")
    @OperLog(module = "统一认证", operation = "手动同步认证中心用户")
    @RequirePerm("system:user:edit")
    @PostMapping("/sync")
    public Result<UserSyncResultVO> sync() {
        return Result.ok(userSyncService.sync());
    }

    /**
     * 应用级启停用户：调认证中心 PUT /service/apps/erp-web/users/{工号}/status，
     * 仅影响本系统进门权，随后本地 status 同步翻转。
     * 无工号的本地降级账号（如演示 admin）直接本地翻转。
     */
    @Operation(summary = "应用级启停用户(调认证中心)")
    @OperLog(module = "统一认证", operation = "应用级启停用户")
    @RequirePerm("system:user:edit")
    @PutMapping("/users/{id}/app-status")
    public Result<Void> appStatus(@PathVariable Long id, @Valid @RequestBody AppStatusForm form) {
        userSyncService.changeAppStatus(id, Boolean.TRUE.equals(form.getEnabled()));
        return Result.ok();
    }

    /** 应用级启停请求体 */
    @lombok.Data
    public static class AppStatusForm {
        /** true=启用 false=禁用 */
        @jakarta.validation.constraints.NotNull(message = "enabled 不能为空")
        private Boolean enabled;
    }
}
