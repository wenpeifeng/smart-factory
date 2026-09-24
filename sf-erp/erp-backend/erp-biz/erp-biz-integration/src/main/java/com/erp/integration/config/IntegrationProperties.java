package com.erp.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ERP-MES 集成配置（对应 application.yml 的 {@code erp.integration.*}）。
 * <p>MES 未就绪时使用 {@code mode=stub}（默认）：客户端仅记录日志视为成功，
 * outbox 直接标记 mock 成功，联调契约先行（与 MES 侧 stub 策略对称）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.integration")
public class IntegrationProperties {

    /** MES 集成端配置 */
    private Mes mes = new Mes();

    /** MES 集成端配置项 */
    @Data
    public static class Mes {
        /** 客户端模式：stub 契约桩 / http 真实调用 */
        private String mode = "stub";
        /** MES 开放接口基地址（http 模式生效，MES 侧 /integration/erp/**） */
        private String baseUrl = "http://localhost:8081/api";
        /** 下推 MES 的令牌（请求头 X-Integration-Token，与 mes.integration.erp.inbound-token 一致） */
        private String outboundToken = "erp-integration-token";
        /** MES 回传入站令牌（请求头 X-Integration-Token 校验，与 mes.integration.erp 配置的推送令牌一致） */
        private String inboundToken = "erp-integration-token";
        /** outbox 重试定时任务开关 */
        private boolean retryEnabled = true;
        /** 主数据每日全量对账推送开关 */
        private boolean masterdataReconcileEnabled = true;
        /** 主数据对账推送时刻（cron，默认每日凌晨 2 点） */
        private String masterdataReconcileCron = "0 0 2 * * ?";
    }
}
