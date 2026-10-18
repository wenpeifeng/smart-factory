package com.mes.integration.controller;

import com.mes.common.result.Result;
import com.mes.integration.oidc.OidcAuthorizeVO;
import com.mes.integration.oidc.OidcPasswordLoginForm;
import com.mes.integration.oidc.OidcTokenVO;
import com.mes.integration.service.OidcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OIDC 登录入口（无需登录态）。
 * <p>/sso/callback 已在安全白名单（授权码由 state 一次性校验保护）。
 */
@Tag(name = "集成-OIDC登录")
@RestController
@RequestMapping("/integration/oidc")
@RequiredArgsConstructor
public class OidcController {

    private final OidcService oidcService;

    /**
     * 获取授权跳转 URL（前端跳转 auth-center 登录页）。
     */
    @Operation(summary = "获取OIDC授权跳转URL")
    @GetMapping("/authorize-url")
    public Result<OidcAuthorizeVO> authorizeUrl() {
        return Result.ok(oidcService.buildAuthorizeUrl());
    }

    /** 企业账号原生登录：认证与 OIDC 授权均由服务端完成，浏览器不跳出 MES */
    @Operation(summary = "企业账号原生登录")
    @PostMapping("/password-login")
    public Result<com.mes.system.dto.auth.LoginVO> passwordLogin(
            @Valid @RequestBody OidcPasswordLoginForm form) {
        return Result.ok(oidcService.loginByPassword(form));
    }

    /**
     * 授权码 SSO 登录（前端回调页调用：换令牌 + 验签 + 签发本系统会话）。
     */
    @Operation(summary = "SSO 授权码登录")
    @PostMapping("/login")
    public Result<com.mes.system.dto.auth.LoginVO> login(@RequestParam String code, @RequestParam String state) {
        return Result.ok(oidcService.loginByCode(code, state));
    }

    /**
     * 授权码换令牌（协议调试用，正常流程走 /login）。
     */
    @Operation(summary = "授权码换令牌")
    @PostMapping("/token")
    public Result<OidcTokenVO> token(@RequestParam String code, @RequestParam String state) {
        return Result.ok(oidcService.exchangeToken(code, state));
    }
}
