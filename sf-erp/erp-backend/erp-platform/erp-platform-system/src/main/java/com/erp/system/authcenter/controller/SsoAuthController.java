package com.erp.system.authcenter.controller;

import com.erp.common.result.Result;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import com.erp.system.authcenter.dto.SsoCallbackForm;
import com.erp.system.authcenter.dto.SsoPasswordLoginForm;
import com.erp.system.authcenter.dto.UserSyncResultVO;
import com.erp.system.authcenter.oidc.OidcAuthorizeVO;
import com.erp.system.authcenter.service.AuthUserSyncService;
import com.erp.system.authcenter.service.SsoLoginService;
import com.erp.system.authcenter.config.AuthCenterProperties;
import com.erp.framework.event.IdentityChangeEvent;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
    private final AuthCenterProperties authCenterProperties;

    /** 发起 SSO：生成 state+PKCE verifier（Redis 5 分钟），返回统一登录页跳转 URL */
    @Operation(summary = "获取统一登录跳转URL")
    @GetMapping("/authorize")
    public Result<OidcAuthorizeVO> authorize() {
        return Result.ok(ssoLoginService.buildAuthorizeUrl());
    }

    /** 企业账号原生登录：认证与 OIDC 授权均由服务端完成，浏览器不跳出 ERP */
    @Operation(summary = "企业账号原生登录")
    @PostMapping("/password-login")
    public Result<LoginVO> passwordLogin(@Valid @RequestBody SsoPasswordLoginForm form) {
        return Result.ok(ssoLoginService.loginByPassword(form));
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

    /** 认证中心事务提交后的内部实时对账入口（令牌鉴权，不依赖用户 JWT）。 */
    @PostMapping("/internal/sync")
    public ResponseEntity<Result<UserSyncResultVO>> internalSync(
            @RequestHeader(value = "X-Identity-Sync-Token", required = false) String token,
            @RequestParam(defaultValue = IdentityChangeEvent.SOURCE_GROUP) String source,
            @RequestParam(required = false) String reason) {
        if (!validSyncToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail(HttpStatus.UNAUTHORIZED.value(), "身份同步回调令牌无效"));
        }
        return ResponseEntity.ok(Result.ok(userSyncService.sync(source)));
    }

    private boolean validSyncToken(String actual) {
        String expected = authCenterProperties.getSyncCallbackToken();
        return actual != null && expected != null && !expected.isBlank() && MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
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

    @Operation(summary = "维护认证中心员工基础档案")
    @OperLog(module = "统一认证", operation = "维护员工基础档案")
    @RequirePerm("system:user:edit")
    @PutMapping("/users/{id}/profile")
    public Result<Void> updateProfile(@PathVariable Long id, @Valid @RequestBody ProfileForm form) {
        userSyncService.updateProfile(id, new AuthUserSyncService.ProfileForm(
                form.getRealName(), form.getPosition(), form.getEmail(), form.getPhone()));
        return Result.ok();
    }

    @Operation(summary = "重置认证中心统一账号密码")
    @OperLog(module = "统一认证", operation = "重置统一账号密码")
    @RequirePerm("system:user:edit")
    @PutMapping("/users/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordForm form) {
        userSyncService.resetPassword(id, form.getPassword());
        return Result.ok();
    }

    /** 应用级启停请求体 */
    @lombok.Data
    public static class AppStatusForm {
        /** true=启用 false=禁用 */
        @jakarta.validation.constraints.NotNull(message = "enabled 不能为空")
        private Boolean enabled;
    }

    @lombok.Data
    public static class ProfileForm {
        @jakarta.validation.constraints.NotBlank(message = "姓名不能为空")
        @jakarta.validation.constraints.Size(max = 64)
        private String realName;
        @jakarta.validation.constraints.Size(max = 64)
        private String position;
        @jakarta.validation.constraints.Size(max = 128)
        private String email;
        @jakarta.validation.constraints.Size(max = 32)
        private String phone;
    }

    @lombok.Data
    public static class PasswordForm {
        @jakarta.validation.constraints.NotBlank(message = "新密码不能为空")
        @jakarta.validation.constraints.Size(min = 6, max = 64, message = "密码长度须为 6-64 位")
        private String password;
    }
}
