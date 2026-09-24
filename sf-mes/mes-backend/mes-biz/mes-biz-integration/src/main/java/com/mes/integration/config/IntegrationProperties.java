package com.mes.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集成模块配置（ERP 双向集成 + auth-center 用户同步/OIDC）。
 * <p>
 * 对应 application.yml 中 {@code mes.integration.*}。两端均未就绪时使用
 * {@code mode=stub}（默认）：客户端仅记录日志并返回成功结果，联调契约先行。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mes.integration")
public class IntegrationProperties {

    /** ERP 集成端配置 */
    private Erp erp = new Erp();

    /** auth-center（统一认证中心）配置 */
    private AuthCenter authCenter = new AuthCenter();

    /** ERP 集成端配置项 */
    @Data
    public static class Erp {
        /** 客户端模式：stub 契约桩 / http 真实调用 */
        private String mode = "stub";
        /** ERP 开放接口基地址（http 模式生效） */
        private String baseUrl = "http://localhost:8080/api";
        /** ERP 下推接口的入站令牌（请求头 X-Integration-Token），stub 模式不校验 */
        private String inboundToken = "erp-integration-token";
        /** 调 ERP 入站接口的出站令牌（= erp.integration.mes.inbound-token） */
        private String outboundToken = "erp-integration-token";
        /** 回传失败重试定时任务开关 */
        private boolean retryEnabled = true;
    }

    /** auth-center 配置项（端点契约以其仓库 docs 为准） */
    @Data
    public static class AuthCenter {
        /** 客户端模式：stub 契约桩 / http 真实调用 */
        private String mode = "stub";
        /** auth-center 基地址（含 context-path） */
        private String baseUrl = "http://localhost:9000";
        /** OIDC client_id（auth-center 登记后填入） */
        private String clientId = "mes-web";
        /** 统一登录页地址（auth-web 前端，SPA 版授权码流入口） */
        private String authorizePageUrl = "http://localhost:5174/oauth/authorize";
        /** OIDC client_secret（公开客户端 PKCE 可空；服务间同步 API 使用） */
        private String clientSecret = "";
        /** 授权码回调地址（本系统对外可达地址） */
        private String redirectUri = "http://localhost:5173/sso/callback";
        /** 服务间同步 API 令牌（请求头 X-Service-Token） */
        private String serviceToken = "auth-center-sync-token";
    }
}
