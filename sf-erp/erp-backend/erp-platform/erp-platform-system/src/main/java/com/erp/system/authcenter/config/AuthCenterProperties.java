package com.erp.system.authcenter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一认证中心（sf-auth）接入配置。
 * <p>
 * 对应 application.yml 中 {@code auth.center.*}；契约见
 * sf-auth 仓库 docs/业务系统接入指南.md（OIDC 授权码+PKCE / JWKS / 服务间 SCIM 同步）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.center")
public class AuthCenterProperties {

    /** 认证中心基地址（issuer，含 context-path） */
    private String baseUrl = "http://localhost:8082/api";

    /** 统一登录页（auth-web 前端，SPA 授权码流入口） */
    private String authorizePageUrl = "http://localhost:5174/oauth/authorize";

    /** OIDC client_id（认证中心应用管理注册） */
    private String clientId = "erp-web";

    /** client_secret（走配置项，不进代码库） */
    private String clientSecret = "demo-secret";

    /** 授权码回调地址（ERP 前端回调页） */
    private String redirectUri = "http://localhost:5172/sso/callback";

    /** 用户同步定时任务开关 */
    private boolean syncEnabled = true;

    /** 用户同步 cron（实时回调失败时，默认每分钟兜底对账） */
    private String syncCron = "0 * * * * ?";

    /** 认证中心实时同步回调令牌。 */
    private String syncCallbackToken = "identity-sync-token";
}
