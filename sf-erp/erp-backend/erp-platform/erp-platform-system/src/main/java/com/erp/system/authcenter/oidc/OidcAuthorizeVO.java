package com.erp.system.authcenter.oidc;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SSO 授权跳转信息：前端拿到后 location.href 跳统一登录页。
 */
@Data
@AllArgsConstructor
public class OidcAuthorizeVO {

    /** 统一登录页完整 URL（含 client_id/redirect_uri/scope/state/PKCE S256） */
    private String authorizeUrl;

    /** 本次请求 state（回调校验用） */
    private String state;
}
