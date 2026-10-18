package com.mes.integration.oidc;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MES 原生企业账号登录表单。
 * <p>账号口令只转交认证中心完成本次认证，不落库、不写日志；
 * 后续仍使用 OIDC 授权码 + PKCE 建立 MES 本地会话。
 */
@Data
public class OidcPasswordLoginForm {

    /** 认证中心账号或工号 */
    @NotBlank(message = "账号不能为空")
    private String username;

    /** 认证中心口令（仅 HTTPS 传输） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
