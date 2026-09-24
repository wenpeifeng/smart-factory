package com.erp.system.authcenter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SSO 回调请求体：统一登录页重定向回 /sso/callback?code&state 后，
 * 前端回调页将参数 POST 到 /auth/sso/callback 换取本地 ERP 会话。
 */
@Data
public class SsoCallbackForm {

    /** 授权码（一次性，5 分钟有效） */
    @NotBlank(message = "code 不能为空")
    private String code;

    /** 授权请求原样回传的 state（一次性消费，防 CSRF/重放） */
    @NotBlank(message = "state 不能为空")
    private String state;
}
