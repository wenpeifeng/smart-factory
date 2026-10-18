package com.erp.system.authcenter.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.framework.context.OrgContextHolder;
import com.erp.system.authcenter.config.AuthCenterProperties;
import com.erp.system.authcenter.dto.SsoCallbackForm;
import com.erp.system.authcenter.dto.SsoPasswordLoginForm;
import com.erp.system.authcenter.mapper.AuthCenterUserMapper;
import com.erp.system.authcenter.oidc.OidcAuthorizeVO;
import com.erp.system.authcenter.oidc.PkceUtil;
import com.erp.system.authcenter.service.SsoLoginService;
import com.erp.system.dto.auth.LoginVO;
import com.erp.system.dto.auth.OrgVO;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.OrgDirectoryMapper;
import com.erp.system.mapper.SysUserOrgMapper;
import com.erp.system.security.JwtTokenProvider;
import com.erp.system.service.SysUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO 登录服务实现（对接 sf-auth，SPA 版授权码 + PKCE）。
 * <p>令牌链路：POST {issuer}/oauth/token（Basic erp-web:secret + code_verifier）
 * → access_token 用 JWKS（RS256，按 kid，本地缓存）验签，校验 aud/exp
 * （iss 缺失放宽、存在则必须匹配 issuer）→ employee_no 匹配本地
 * sys_user.employee_no（同步副本）→ 签发本地 ERP JWT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoLoginServiceImpl implements SsoLoginService {

    /** state → verifier 的 Redis 键前缀 */
    private static final String STATE_KEY_PREFIX = "authcenter:sso:state:";

    /** state 有效期（分钟） */
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final StringRedisTemplate redis;
    private final AuthCenterProperties properties;
    private final AuthCenterUserMapper userMapper;
    private final com.erp.system.authcenter.service.AuthUserSyncService authUserSyncService;
    private final SysUserService userService;
    private final OrgDirectoryMapper orgDirectoryMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final JwtTokenProvider tokenProvider;

    /** 认证中心授予本系统超管的角色标签（与同步对账同源） */
    private static final String ADMIN_ROLE_TAG = "erp.admin";

    /** JWKS 公钥缓存（kid → RSAPublicKey；kid 未命中时刷新） */
    private final Map<String, PublicKey> jwksCache = new ConcurrentHashMap<>();

    private final RestClient restClient = RestClient.create();

    @Override
    public OidcAuthorizeVO buildAuthorizeUrl() {
        AuthorizationContext context = newAuthorizationContext();

        String url = UriComponentsBuilder.fromHttpUrl(properties.getAuthorizePageUrl())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile")
                .queryParam("state", context.state())
                .queryParam("code_challenge", context.challenge())
                .queryParam("code_challenge_method", "S256")
                .toUriString();
        return new OidcAuthorizeVO(url, context.state());
    }

    @Override
    public LoginVO loginByPassword(SsoPasswordLoginForm form) {
        AuthorizationContext context = newAuthorizationContext();
        String centerToken = null;
        try {
            centerToken = requestCenterLogin(form);
            String redirectUri = requestAuthorization(centerToken, context);
            SsoCallbackForm callback = new SsoCallbackForm();
            callback.setCode(extractAuthorizationCode(redirectUri));
            callback.setState(context.state());
            return loginByCode(callback);
        } finally {
            // loginByCode 会一次性消费；前序失败时也要主动清掉，避免无效 state 堆积。
            redis.delete(STATE_KEY_PREFIX + context.state());
            logoutCenterSession(centerToken);
        }
    }

    @Override
    public LoginVO loginByCode(SsoCallbackForm form) {
        String verifier = consumeState(form.getState());
        TokenResp resp = requestToken(form.getCode(), verifier);
        Claims claims = verifyToken(resp.accessToken());
        String employeeNo = claims.get("employee_no", String.class);
        if (!StringUtils.hasText(employeeNo)) {
            throw BusinessException.ofKey("erp.system.sso.employee-no-missing");
        }
        SysUser user = userMapper.selectByEmployeeNo(employeeNo);
        if (user == null) {
            // JIT 建档：能拿到有效 code 即已在中心完成登录且持有本应用进门权，
            // 本地无副本时直接按 claims 建档（零依赖定时同步/列表接口，新分配用户首登即可用）
            user = authUserSyncService.jitCreate(employeeNo, claims.get("name", String.class),
                    roleNames(claims).contains(ADMIN_ROLE_TAG));
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.ofKey("erp.system.sso.user-disabled", employeeNo);
        }
        Long tenantId = user.getTenantId() != null ? user.getTenantId() : OrgContextHolder.DEFAULT_TENANT_ID;
        Long orgId = user.getOrgId() != null ? user.getOrgId() : OrgContextHolder.DEFAULT_ORG_ID;
        String token = tokenProvider.createToken(user.getId(), tenantId, orgId);
        log.info("[SSO] 统一认证登录成功: employeeNo={}, userId={}, roles={}",
                employeeNo, user.getId(), claims.get("roles"));
        return new LoginVO(token, userService.getDetail(user.getId()), tenantId, orgId,
                listSwitchableOrgs(user.getId()));
    }


    /** claims.roles → 字符串集合（JIT 判定 erp.admin 用） */
    @SuppressWarnings("unchecked")
    private Set<String> roleNames(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            Set<String> names = new HashSet<>();
            for (Object r : list) {
                if (r != null) {
                    names.add(String.valueOf(r));
                }
            }
            return names;
        }
        return Set.of();
    }

    /** state 一次性消费（防重放）并取回 verifier */
    private String consumeState(String state) {
        String verifier = redis.opsForValue().get(STATE_KEY_PREFIX + state);
        redis.delete(STATE_KEY_PREFIX + state);
        if (!StringUtils.hasText(state) || verifier == null) {
            throw BusinessException.ofKey("erp.system.sso.state-invalid");
        }
        return verifier;
    }

    /** 创建一次授权事务并暂存 PKCE verifier。 */
    private AuthorizationContext newAuthorizationContext() {
        String state = randomToken();
        String verifier = PkceUtil.newVerifier();
        redis.opsForValue().set(STATE_KEY_PREFIX + state, verifier, STATE_TTL);
        return new AuthorizationContext(state, PkceUtil.challenge(verifier));
    }

    /** 使用企业账号向认证中心建立一次短生命周期管理会话。 */
    private String requestCenterLogin(SsoPasswordLoginForm form) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", form.getUsername());
        body.put("password", form.getPassword());
        body.put("loginType", "OIDC");
        body.put("clientId", properties.getClientId());
        try {
            JsonNode result = restClient.post()
                    .uri(properties.getBaseUrl() + "/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, LocaleContextHolder.getLocale().toLanguageTag())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = requireCenterSuccess(result, "erp.system.sso.center-login-failed");
            String token = data.path("token").asText();
            if (!StringUtils.hasText(token)) {
                throw BusinessException.ofKey("erp.system.sso.center-login-failed", "认证中心未返回登录令牌");
            }
            return token;
        } catch (RestClientResponseException e) {
            throw BusinessException.ofKey("erp.system.sso.center-login-failed", centerHttpMessage(e));
        } catch (RestClientException e) {
            throw BusinessException.ofKey("erp.system.sso.center-unavailable");
        }
    }

    /** 携中心会话直接签发授权码；consent=true 使普通登录无需再展示授权确认页。 */
    private String requestAuthorization(String centerToken, AuthorizationContext context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", properties.getClientId());
        body.put("redirectUri", properties.getRedirectUri());
        body.put("responseType", "code");
        body.put("scope", "openid profile");
        body.put("state", context.state());
        body.put("codeChallenge", context.challenge());
        body.put("codeChallengeMethod", "S256");
        body.put("consent", true);
        try {
            JsonNode result = restClient.post()
                    .uri(properties.getBaseUrl() + "/oauth/authorize")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + centerToken)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, LocaleContextHolder.getLocale().toLanguageTag())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = requireCenterSuccess(result, "erp.system.sso.center-authorize-failed");
            String redirectUri = data.path("redirectUri").asText();
            if (data.path("needConsent").asBoolean(false) || !StringUtils.hasText(redirectUri)) {
                throw BusinessException.ofKey("erp.system.sso.center-authorize-failed", "认证中心未签发授权码");
            }
            return redirectUri;
        } catch (RestClientResponseException e) {
            throw BusinessException.ofKey("erp.system.sso.center-authorize-failed", centerHttpMessage(e));
        } catch (RestClientException e) {
            throw BusinessException.ofKey("erp.system.sso.center-unavailable");
        }
    }

    /** 校验认证中心统一 Result 响应并返回 data。 */
    private JsonNode requireCenterSuccess(JsonNode result, String messageKey) {
        if (result == null || result.path("code").asInt(-1) != 0 || result.path("data").isMissingNode()) {
            String message = result == null ? "认证中心返回为空" : result.path("message").asText("认证中心处理失败");
            throw BusinessException.ofKey(messageKey, message);
        }
        return result.path("data");
    }

    /** 从认证中心返回的回调地址中提取一次性授权码。 */
    private String extractAuthorizationCode(String redirectUri) {
        String code = UriComponentsBuilder.fromUriString(redirectUri).build()
                .getQueryParams().getFirst("code");
        if (!StringUtils.hasText(code)) {
            throw BusinessException.ofKey("erp.system.sso.center-authorize-failed", "回调地址缺少授权码");
        }
        return code;
    }

    /** 中心会话仅用于本次授权，授权码签发后立即注销，避免产生孤立在线会话。 */
    private void logoutCenterSession(String centerToken) {
        if (!StringUtils.hasText(centerToken)) {
            return;
        }
        try {
            restClient.post()
                    .uri(properties.getBaseUrl() + "/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + centerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("[SSO] 认证中心临时会话注销失败，等待令牌自然过期");
        }
    }

    /** 提取中心 HTTP 错误文案，不回显响应中的其他敏感字段。 */
    private String centerHttpMessage(RestClientResponseException e) {
        try {
            JsonNode result = MAPPER.readTree(e.getResponseBodyAsString());
            return result.path("message").asText("HTTP " + e.getStatusCode().value());
        } catch (Exception ignored) {
            return "HTTP " + e.getStatusCode().value();
        }
    }

    /** 授权码 + PKCE 换令牌（Basic 认证 client_id:client_secret） */
    private TokenResp requestToken(String code, String verifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("code_verifier", verifier);
        try {
            TokenResp resp = restClient.post()
                    .uri(properties.getBaseUrl() + "/oauth/token")
                    .headers(h -> h.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResp.class);
            if (resp == null || !StringUtils.hasText(resp.accessToken())) {
                throw BusinessException.ofKey("erp.system.sso.token-exchange-failed", 0, "empty response");
            }
            return resp;
        } catch (RestClientResponseException e) {
            throw BusinessException.ofKey("erp.system.sso.token-exchange-failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    /**
     * JWKS 验签（RS256，按 kid 选公钥）+ aud/exp 校验后返回 claims。
     * <p>iss 校验放宽：auth-center 令牌可能不携带 iss claim（文档与实现有出入），
     * 缺失时跳过、存在则必须匹配 issuer 配置。
     */
    private Claims verifyToken(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            JsonNode header = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
            String kid = header.path("kid").asText(null);
            Claims claims = Jwts.parser().verifyWith(publicKey(kid))
                    .requireAudience(properties.getClientId())
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            String iss = claims.getIssuer();
            if (iss != null && !properties.getBaseUrl().equals(iss)) {
                throw BusinessException.ofKey("erp.system.sso.issuer-mismatch", iss);
            }
            return claims;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.system.sso.token-verify-failed", e.getMessage());
        }
    }

    /** 取 JWKS 公钥（缓存；kid 未命中时强制刷新一次） */
    private PublicKey publicKey(String kid) {
        if (kid != null && jwksCache.containsKey(kid)) {
            return jwksCache.get(kid);
        }
        refreshJwks();
        PublicKey key = jwksCache.get(kid);
        if (key == null) {
            throw BusinessException.ofKey("erp.system.sso.jwks-kid-missing", String.valueOf(kid));
        }
        return key;
    }

    /** 拉取 JWKS 并重建缓存（GET {issuer}/.well-known/jwks.json） */
    private void refreshJwks() {
        try {
            String body = restClient.get()
                    .uri(properties.getBaseUrl() + "/.well-known/jwks.json")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(String.class);
            JsonNode keys = MAPPER.readTree(body).path("keys");
            for (JsonNode k : keys) {
                if (!"RS256".equals(k.path("alg").asText())) {
                    continue;
                }
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("e").asText()));
                jwksCache.put(k.path("kid").asText(),
                        KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            }
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.system.sso.jwks-fetch-failed", e.getMessage());
        }
    }

    /**
     * 可切换组织列表（与 AuthServiceImpl 同规则）：
     * 有绑定 → 绑定 ∩ 启用；无绑定 → 全部启用组织。
     */
    private List<OrgVO> listSwitchableOrgs(Long userId) {
        List<OrgVO> enabled = orgDirectoryMapper.selectEnabledOrgs();
        List<Long> boundOrgIds = userOrgMapper.selectOrgIdsByUserId(userId);
        if (boundOrgIds.isEmpty()) {
            return enabled;
        }
        Set<Long> bound = new HashSet<>(boundOrgIds);
        return enabled.stream().filter(org -> bound.contains(org.getId())).toList();
    }

    /** 16 字节随机 state（Base64Url） */
    private String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** /oauth/token 响应（OAuth2 snake_case） */
    record TokenResp(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("id_token") String idToken,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn) {
    }

    /** 一次 OIDC 授权事务的公开部分；verifier 只保存在 Redis。 */
    private record AuthorizationContext(String state, String challenge) {
    }
}
