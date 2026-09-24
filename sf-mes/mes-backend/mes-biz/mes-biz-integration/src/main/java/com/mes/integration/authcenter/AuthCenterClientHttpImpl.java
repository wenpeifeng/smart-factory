package com.mes.integration.authcenter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.Result;
import com.mes.integration.authcenter.dto.AuthCenterUserDTO;
import com.mes.integration.config.IntegrationProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * auth-center 服务间客户端 http 实现（mode=http）。
 * <p>service token（client_credentials）Redis 缓存至到期前 60 秒；
 * 调用失败统一转 BusinessException 供同步任务/管理端提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mes.integration.auth-center", name = "mode", havingValue = "http")
public class AuthCenterClientHttpImpl implements AuthCenterClient {

    private static final String SERVICE_TOKEN_KEY = "mes:integration:auth-service-token";

    private final IntegrationProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient = RestClient.create();

    @Override
    public List<AuthCenterUserDTO> fetchUsers(String updatedSince) {
        String query = updatedSince != null ? "?updatedSince=" + updatedSince : "";
        String body = get("/service/apps/{clientId}/users" + query, properties.getAuthCenter().getClientId());
        SyncResp resp = parse(body, SyncResp.class);
        return resp.getData();
    }

    @Override
    public void disableUser(String employeeNo) {
        putStatus(employeeNo, false);
    }

    @Override
    public void enableUser(String employeeNo) {
        putStatus(employeeNo, true);
    }

    private void putStatus(String employeeNo, boolean enabled) {
        try {
            restClient.put()
                    .uri(properties.getAuthCenter().getBaseUrl() + "/service/apps/{clientId}/users/{employeeNo}/status",
                            properties.getAuthCenter().getClientId(), employeeNo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StatusForm(enabled))
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException("auth-center 应用级启停失败(%d): %s", e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    /** GET 服务间接口（Result 包装，data 为用户数组） */
    private String get(String path, Object... uriVars) {
        try {
            ResponseEntity<String> resp = restClient.get()
                    .uri(properties.getAuthCenter().getBaseUrl() + path, uriVars)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .retrieve()
                    .toEntity(String.class);
            return resp.getBody();
        } catch (RestClientResponseException e) {
            throw new BusinessException("auth-center 用户同步失败(%d): %s", e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    /** client_credentials 换 service token（带 Redis 缓存） */
    private String serviceToken() {
        String cached = redisTemplate.opsForValue().get(SERVICE_TOKEN_KEY);
        if (cached != null) {
            return cached;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        try {
            TokenResp resp = restClient.post()
                    .uri(properties.getAuthCenter().getBaseUrl() + "/oauth/token")
                    .headers(h -> h.setBasicAuth(properties.getAuthCenter().getClientId(),
                            properties.getAuthCenter().getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResp.class);
            if (resp == null || resp.getAccessToken() == null) {
                throw new BusinessException("auth-center service token 获取失败: 空响应");
            }
            long ttl = Math.max(60, (resp.getExpiresIn() == null ? 3600 : resp.getExpiresIn()) - 60);
            redisTemplate.opsForValue().set(SERVICE_TOKEN_KEY, resp.getAccessToken(), Duration.ofSeconds(ttl));
            return resp.getAccessToken();
        } catch (RestClientResponseException e) {
            throw new BusinessException("auth-center service token 获取失败(%d): %s",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException("auth-center 响应解析失败: %s", e.getMessage());
        }
    }

    /** /oauth/token 响应（OAuth2 标准 snake_case） */
    @Data
    static class TokenResp {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        private String accessToken;
        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        private Long expiresIn;
    }

    /** 同步接口 Result 包装 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SyncResp {
        private Integer code;
        private String message;
        private List<AuthCenterUserDTO> data;
    }

    /** 启停请求体 */
    record StatusForm(boolean enabled) {
    }
}
