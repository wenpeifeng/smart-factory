package com.erp.system.authcenter.service;

import com.erp.system.authcenter.dto.SsoCallbackForm;
import com.erp.system.authcenter.dto.SsoPasswordLoginForm;
import com.erp.system.authcenter.oidc.OidcAuthorizeVO;
import com.erp.system.dto.auth.LoginVO;

/**
 * 统一认证中心 SSO 登录服务（OIDC 授权码 + PKCE）。
 * <p>流程：GET /auth/sso/authorize 生成 state+verifier（Redis 5 分钟）→
 * 跳统一登录页 → 回调页 POST /auth/sso/callback → 后端换令牌 →
 * JWKS 验签（iss/aud/exp）→ 按 employee_no 匹配本地 sys_user →
 * 用本地 JwtTokenProvider 签发 ERP JWT（与账密登录同构返回）。
 */
public interface SsoLoginService {

    /**
     * 构造统一登录页跳转 URL（state → PKCE verifier 存 Redis 5 分钟）。
     *
     * @return 授权 URL + state
     */
    OidcAuthorizeVO buildAuthorizeUrl();

    /**
     * 在 ERP 登录页直接使用认证中心账号登录。
     * <p>服务端代为完成中心认证、授权码签发与 PKCE 换令牌，浏览器无需离开 ERP。
     *
     * @param form 企业账号与口令
     * @return ERP 本地登录会话
     */
    LoginVO loginByPassword(SsoPasswordLoginForm form);

    /**
     * 授权码 SSO 登录：校验 state（一次性）→ 换令牌 → 验签取 claims →
     * employee_no 匹配本地用户（未同步/已禁用明确报错）→ 签发本地 ERP JWT。
     *
     * @param form 回调参数（code + state）
     * @return 与本地账密登录同构的 LoginVO（token/user/tenantId/orgId/orgs）
     */
    LoginVO loginByCode(SsoCallbackForm form);
}
