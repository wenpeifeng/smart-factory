package com.erp.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 集成入站接口安全链（模块内闭环，不改共享 SecurityConfig）。
 * <p>{@code /integration/mes/**} 是系统间回调：无登录上下文（MES 不持有 ERP JWT），
 * 鉴权由 {@code MesInboundController} 的 X-Integration-Token 请求头校验承担，
 * 与 MES 侧 /integration/erp/** 的 stub 阶段静态令牌策略对称。
 * <p>实现方式：注册一条 {@code @Order(1)} 的独立过滤链仅匹配本模块路径，
 * 其余请求仍走 erp-framework SecurityConfig 的默认链（无 @Order = 最低优先级），
 * 零侵入共享文件。若后续约定改为集中白名单，可在 SecurityConfig whitelist
 * 追加 {@code /integration/mes/**} 并删除本类。
 */
@Configuration
public class IntegrationSecurityConfig {

    /** MES 回传入站路径（context-path=/api 之下） */
    public static final String INBOUND_PATH = "/integration/mes/**";

    /**
     * 入站专用链：无状态 + 全放行（令牌校验在 Controller 层）。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain integrationInboundFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(INBOUND_PATH)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
