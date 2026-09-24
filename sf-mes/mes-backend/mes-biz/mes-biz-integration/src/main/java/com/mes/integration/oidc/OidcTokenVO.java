package com.mes.integration.oidc;

import lombok.Data;

/**
 * OIDC 令牌响应（/token 端点契约：RFC 6749 4.1.4 + OIDC id_token）。
 */
@Data
public class OidcTokenVO {
    /** 访问令牌 */
    private String accessToken;
    /** OIDC 身份令牌（JWT，含 sub/roles claim） */
    private String idToken;
    /** 刷新令牌 */
    private String refreshToken;
    /** 令牌类型（Bearer） */
    private String tokenType;
    /** 有效期（秒） */
    private Long expiresIn;
}
