package com.mes.integration.oidc;

import lombok.Data;

/**
 * OIDC 授权跳转信息：前端拿到后 location.href 跳转 auth-center 登录页。
 */
@Data
public class OidcAuthorizeVO {
    /** auth-center 授权端点完整 URL（含 client_id/redirect_uri/PKCE/state） */
    private String authorizeUrl;
    /** 本次请求 state（回调校验） */
    private String state;
}
