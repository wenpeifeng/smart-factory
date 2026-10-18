package com.erp.system.authcenter.client;

import com.erp.common.exception.BusinessException;
import com.erp.system.authcenter.config.AuthCenterProperties;
import com.erp.system.authcenter.dto.AuthCenterUserDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * 认证中心服务间客户端 http 实现。
 * <p>service token（grant_type=client_credentials，Basic 认证）Redis 缓存至
 * 到期前 60 秒（键 {@code authcenter:service-token}）；调用失败统一转
 * BusinessException（i18n）供同步任务/管理端提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCenterClientHttpImpl implements AuthCenterClient {

    /** service token 的 Redis 缓存键 */
    private static final String SERVICE_TOKEN_KEY = "authcenter:service-token";

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final AuthCenterProperties properties;
    private final StringRedisTemplate redis;
    private final RestClient restClient = RestClient.create();

    @Override
    public List<AuthCenterUserDTO> fetchUsers(String updatedSince) {
        String query = updatedSince != null ? "?updatedSince=" + updatedSince : "";
        String body;
        try {
            body = restClient.get()
                    .uri(properties.getBaseUrl() + "/service/apps/{clientId}/users" + query,
                            properties.getClientId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw BusinessException.ofKey("erp.system.sso.sync-fetch-failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }
        try {
            SyncResp resp = MAPPER.readValue(body, SyncResp.class);
            requireSuccess(resp.getCode(), resp.getMessage());
            return resp.getData() == null ? List.of() : resp.getData();
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw BusinessException.ofKey("erp.system.sso.sync-parse-failed", e.getMessage());
        }
    }

    @Override
    public void disableUser(String employeeNo) {
        putStatus(employeeNo, false);
    }

    @Override
    public void enableUser(String employeeNo) {
        putStatus(employeeNo, true);
    }

    @Override
    public void updateProfile(String employeeNo, ProfileForm form) {
        putUserResource(employeeNo, "profile", form);
    }

    @Override
    public void resetPassword(String employeeNo, String password) {
        putUserResource(employeeNo, "password", new PasswordForm(password));
    }

    /** 应用级启停（PUT /service/apps/{clientId}/users/{employeeNo}/status） */
    private void putStatus(String employeeNo, boolean enabled) {
        try {
            String responseBody = restClient.put()
                    .uri(properties.getBaseUrl() + "/service/apps/{clientId}/users/{employeeNo}/status",
                            properties.getClientId(), employeeNo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StatusForm(enabled))
                    .retrieve()
                    .body(String.class);
            requireActionSuccess(responseBody);
        } catch (RestClientResponseException e) {
            throw BusinessException.ofKey("erp.system.sso.status-failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    private void putUserResource(String employeeNo, String resource, Object body) {
        try {
            String responseBody = restClient.put()
                    .uri(properties.getBaseUrl() + "/service/apps/{clientId}/users/{employeeNo}/{resource}",
                            properties.getClientId(), employeeNo, resource)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            requireActionSuccess(responseBody);
        } catch (RestClientResponseException e) {
            throw BusinessException.ofKey("erp.system.sso.user-action-failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    /** auth-center 业务异常使用 HTTP 200 + Result.code 表达，服务间客户端必须同时校验业务码。 */
    private void requireActionSuccess(String body) {
        try {
            ActionResp resp = MAPPER.readValue(body, ActionResp.class);
            requireSuccess(resp.getCode(), resp.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.system.sso.sync-parse-failed", e.getMessage());
        }
    }

    private void requireSuccess(Integer code, String message) {
        if (code == null || code != 0) {
            throw BusinessException.ofKey("erp.system.sso.user-action-failed", 200,
                    message == null ? "auth-center business error" : message);
        }
    }

    /** client_credentials 换 service token（Redis 缓存，到期前 60 秒刷新） */
    private String serviceToken() {
        String cached = redis.opsForValue().get(SERVICE_TOKEN_KEY);
        if (cached != null) {
            return cached;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        TokenResp resp;
        try {
            resp = restClient.post()
                    .uri(properties.getBaseUrl() + "/oauth/token")
                    .headers(h -> h.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResp.class);
        } catch (RestClientResponseException e) {
            throw BusinessException.ofKey("erp.system.sso.service-token-failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }
        if (resp == null || resp.getAccessToken() == null) {
            throw BusinessException.ofKey("erp.system.sso.service-token-failed", 0, "empty response");
        }
        long ttl = Math.max(60, (resp.getExpiresIn() == null ? 3600 : resp.getExpiresIn()) - 60);
        redis.opsForValue().set(SERVICE_TOKEN_KEY, resp.getAccessToken(), Duration.ofSeconds(ttl));
        return resp.getAccessToken();
    }

    /** /oauth/token 响应（OAuth2 snake_case） */
    @Data
    static class TokenResp {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ActionResp {
        private Integer code;
        private String message;
    }

    /** 应用级启停请求体 */
    record StatusForm(boolean enabled) {
    }

    record PasswordForm(String password) { }
}
