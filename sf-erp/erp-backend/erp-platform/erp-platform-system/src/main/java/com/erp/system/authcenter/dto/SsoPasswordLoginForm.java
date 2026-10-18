package com.erp.system.authcenter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 子系统原生登录表单。
 * <p>账号口令仅用于本次服务端到认证中心的认证请求，不落库、不写日志；
 * 认证成功后仍按 OIDC 授权码 + PKCE 换取本系统会话。
 */
@Data
public class SsoPasswordLoginForm {

    /** 认证中心账号或工号 */
    @NotBlank(message = "账号不能为空")
    private String username;

    /** 认证中心口令（仅 HTTPS 传输） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
