package com.mes.integration.service;

import com.mes.integration.oidc.OidcAuthorizeVO;
import com.mes.integration.oidc.OidcTokenVO;

/**
 * OIDC 客户端服务（授权码 + PKCE）。
 * <p>登录流程：前端取 authorizeUrl 跳 auth-center -> 登录后回调
 * {@code /sso/callback?code&state} -> 服务端校验 state、换令牌、发本系统 JWT。
 * auth-center 端点契约以其仓库 docs 为准；未就绪前换令牌走 stub。
 */
public interface OidcService {

    /**
     * 构造授权跳转信息（生成 state + PKCE pair，state->verifier 存 Redis 10 分钟）。
     *
     * @return 授权 URL 与 state
     */
    OidcAuthorizeVO buildAuthorizeUrl();

    /**
     * 授权码换令牌。
     *
     * @param code 授权码
     * @param state 回调 state（须与授权时一致）
     * @return 令牌响应
     */
    OidcTokenVO exchangeToken(String code, String state);

    /**
     * 授权码登录（SSO 桥接）：校验 state -> 换令牌 -> 验签/取 claims ->
     * 匹配本地同步用户 -> 签发 MES 会话。
     *
     * @param code  授权码
     * @param state 回调 state
     * @return MES 登录 VO（与本地登录同构，前端直接 setAuth）
     */
    com.mes.system.dto.auth.LoginVO loginByCode(String code, String state);
}
