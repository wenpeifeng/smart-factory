package com.mes.integration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.common.exception.BusinessException;
import com.mes.integration.config.IntegrationProperties;
import com.mes.integration.oidc.OidcAuthorizeVO;
import com.mes.integration.oidc.OidcPasswordLoginForm;
import com.mes.integration.oidc.OidcTokenVO;
import com.mes.integration.oidc.PkceUtil;
import com.mes.integration.service.AuthUserSyncService;
import com.mes.integration.service.OidcService;
import com.mes.system.dto.auth.LoginVO;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.service.AuthService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OIDC 客户端实现（对接 sf-auth，SPA 版授权码 + PKCE，见其 docs/业务系统接入指南.md）。
 * <p>http 模式流程：authorize-url 指向 auth-web 统一登录页；回调后
 * POST /oauth/token（Basic 认证 + code_verifier）换 access/id token；
 * 用 JWKS（RS256，按 kid）本地验签并校验 iss/aud/exp，再以工号
 * employee_no 匹配本地同步用户签发 MES 会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcServiceImpl implements OidcService {

    private static final String STATE_KEY_PREFIX = "mes:oidc:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final StringRedisTemplate redisTemplate;
    private final IntegrationProperties properties;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final AuthUserSyncService authUserSyncService;

    /** JWKS 公钥缓存（kid -> RSAPublicKey；kid 不匹配时刷新） */
    private final Map<String, PublicKey> jwksCache = new ConcurrentHashMap<>();

    private final RestClient restClient = RestClient.create();

    @Override
    public OidcAuthorizeVO buildAuthorizeUrl() {
        AuthorizationContext context = newAuthorizationContext();

        IntegrationProperties.AuthCenter auth = properties.getAuthCenter();
        String url = UriComponentsBuilder.fromHttpUrl(auth.getAuthorizePageUrl())
                .queryParam("client_id", auth.getClientId())
                .queryParam("redirect_uri", auth.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile")
                .queryParam("state", context.state())
                .queryParam("code_challenge", context.challenge())
                .queryParam("code_challenge_method", "S256")
                .toUriString();

        OidcAuthorizeVO vo = new OidcAuthorizeVO();
        vo.setAuthorizeUrl(url);
        vo.setState(context.state());
        return vo;
    }

    @Override
    public LoginVO loginByPassword(OidcPasswordLoginForm form) {
        AuthorizationContext context = newAuthorizationContext();
        String centerToken = null;
        try {
            centerToken = requestCenterLogin(form);
            String redirectUri = requestAuthorization(centerToken, context);
            return loginByCode(extractAuthorizationCode(redirectUri), context.state());
        } finally {
            // loginByCode 会消费 state；前序失败时也要清理，避免无效事务残留。
            redisTemplate.delete(STATE_KEY_PREFIX + context.state());
            logoutCenterSession(centerToken);
        }
    }

    @Override
    public OidcTokenVO exchangeToken(String code, String state) {
        String verifier = consumeState(state);
        TokenResp resp = requestToken(code, verifier);
        OidcTokenVO vo = new OidcTokenVO();
        vo.setAccessToken(resp.accessToken());
        vo.setIdToken(resp.idToken());
        vo.setTokenType(resp.tokenType());
        vo.setExpiresIn(resp.expiresIn());
        return vo;
    }

    @Override
    public LoginVO loginByCode(String code, String state) {
        String verifier = consumeState(state);
        TokenResp resp = requestToken(code, verifier);
        // JWKS 验签 + iss/aud/exp 校验，取 claims
        Claims claims = verifyAccessToken(resp.accessToken());
        // 工号为本地匹配键（须先经用户同步建档）
        String employeeNo = claims.get("employee_no", String.class);
        if (!StringUtils.hasText(employeeNo)) {
            throw new BusinessException("auth-center 令牌缺少 employee_no，无法匹配本地用户");
        }
        SysUser user = sysUserMapper.selectOne(Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getEmployeeNo, employeeNo)
                .last("LIMIT 1"));
        if (user == null) {
            // JIT 建档：授权码流保证该用户已在 auth 录入并通过认证（无录入者拿不到 code）；
            // 直接用验签过的 claims 落地影子账号，不依赖定时同步与中心列表接口
            List<String> roles = claims.get("roles", List.class);
            boolean mesAdmin = roles != null && roles.contains("mes.admin");
            log.info("[OIDC] 工号 {} 本地缺失，JIT 建档（mes.admin={}）", employeeNo, mesAdmin);
            user = authUserSyncService.provision(employeeNo,
                    claims.get("username", String.class),
                    claims.get("name", String.class), mesAdmin);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("用户 %s 已停用（离职/禁用）", employeeNo);
        }
        log.info("[OIDC] SSO 登录成功: employeeNo={}, roles={}", employeeNo, claims.get("roles"));
        return authService.buildSession(user.getId());
    }

    /** state 一次性消费（防重放）并返回 verifier */
    private String consumeState(String state) {
        if (!StringUtils.hasText(state)) {
            throw new BusinessException("state 不能为空");
        }
        String verifier = redisTemplate.opsForValue().get(STATE_KEY_PREFIX + state);
        redisTemplate.delete(STATE_KEY_PREFIX + state);
        if (verifier == null) {
            throw new BusinessException("state 无效或已过期，请重新发起登录");
        }
        return verifier;
    }

    /** 创建一次授权事务并暂存 PKCE verifier。 */
    private AuthorizationContext newAuthorizationContext() {
        String state = randomToken();
        String verifier = PkceUtil.newVerifier();
        redisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, verifier, STATE_TTL);
        return new AuthorizationContext(state, PkceUtil.challenge(verifier));
    }

    /** 使用企业账号向认证中心建立一次短生命周期管理会话。 */
    private String requestCenterLogin(OidcPasswordLoginForm form) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", form.getUsername());
        body.put("password", form.getPassword());
        body.put("loginType", "OIDC");
        body.put("clientId", properties.getAuthCenter().getClientId());
        try {
            JsonNode result = restClient.post()
                    .uri(properties.getAuthCenter().getBaseUrl() + "/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, LocaleContextHolder.getLocale().toLanguageTag())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = requireCenterSuccess(result, "企业账号登录失败");
            String token = data.path("token").asText();
            if (!StringUtils.hasText(token)) {
                throw new BusinessException("企业账号登录失败：认证中心未返回登录令牌");
            }
            return token;
        } catch (RestClientResponseException e) {
            throw new BusinessException("企业账号登录失败：%s", centerHttpMessage(e));
        } catch (RestClientException e) {
            throw new BusinessException("认证服务暂时不可用，请稍后重试或使用应急管理员登录");
        }
    }

    /** 携中心会话直接签发授权码；consent=true 使普通登录无需再展示授权确认页。 */
    private String requestAuthorization(String centerToken, AuthorizationContext context) {
        IntegrationProperties.AuthCenter auth = properties.getAuthCenter();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", auth.getClientId());
        body.put("redirectUri", auth.getRedirectUri());
        body.put("responseType", "code");
        body.put("scope", "openid profile");
        body.put("state", context.state());
        body.put("codeChallenge", context.challenge());
        body.put("codeChallengeMethod", "S256");
        body.put("consent", true);
        try {
            JsonNode result = restClient.post()
                    .uri(auth.getBaseUrl() + "/oauth/authorize")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + centerToken)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, LocaleContextHolder.getLocale().toLanguageTag())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = requireCenterSuccess(result, "应用访问校验失败");
            String redirectUri = data.path("redirectUri").asText();
            if (data.path("needConsent").asBoolean(false) || !StringUtils.hasText(redirectUri)) {
                throw new BusinessException("应用访问校验失败：认证中心未签发授权码");
            }
            return redirectUri;
        } catch (RestClientResponseException e) {
            throw new BusinessException("应用访问校验失败：%s", centerHttpMessage(e));
        } catch (RestClientException e) {
            throw new BusinessException("认证服务暂时不可用，请稍后重试或使用应急管理员登录");
        }
    }

    /** 校验认证中心统一 Result 响应并返回 data。 */
    private JsonNode requireCenterSuccess(JsonNode result, String prefix) {
        if (result == null || result.path("code").asInt(-1) != 0 || result.path("data").isMissingNode()) {
            String message = result == null ? "认证中心返回为空" : result.path("message").asText("认证中心处理失败");
            throw new BusinessException("%s：%s", prefix, message);
        }
        return result.path("data");
    }

    /** 从认证中心回调地址提取一次性授权码。 */
    private String extractAuthorizationCode(String redirectUri) {
        String code = UriComponentsBuilder.fromUriString(redirectUri).build()
                .getQueryParams().getFirst("code");
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("应用访问校验失败：回调地址缺少授权码");
        }
        return code;
    }

    /** 中心会话仅用于本次授权，授权码签发后立即注销。 */
    private void logoutCenterSession(String centerToken) {
        if (!StringUtils.hasText(centerToken)) {
            return;
        }
        try {
            restClient.post()
                    .uri(properties.getAuthCenter().getBaseUrl() + "/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + centerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("[OIDC] 认证中心临时会话注销失败，等待令牌自然过期");
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
        form.add("redirect_uri", properties.getAuthCenter().getRedirectUri());
        form.add("code_verifier", verifier);
        try {
            return restClient.post()
                    .uri(properties.getAuthCenter().getBaseUrl() + "/oauth/token")
                    .headers(h -> h.setBasicAuth(properties.getAuthCenter().getClientId(),
                            properties.getAuthCenter().getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResp.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException("auth-center 换取令牌失败(%d): %s",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    /**
     * JWKS 验签（RS256，按 kid 选公钥）+ aud/exp 校验后返回 claims。
     * <p>iss 校验放宽：auth-center 当前令牌未携带 iss claim（其文档与实现有出入），
     * 缺失时跳过、存在则必须匹配 issuer 配置。
     */
    private Claims verifyAccessToken(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            JsonNode header = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
            String kid = header.path("kid").asText(null);
            PublicKey key = publicKey(kid);
            Claims claims = Jwts.parser().verifyWith(key)
                    .requireAudience(properties.getAuthCenter().getClientId())
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            String iss = claims.getIssuer();
            if (iss != null && !properties.getAuthCenter().getBaseUrl().equals(iss)) {
                throw new BusinessException("access token issuer 不匹配: %s", iss);
            }
            return claims;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("access token 验签失败: %s", e.getMessage());
        }
    }

    /** 取 JWKS 公钥（带缓存；kid 未命中时强制刷新一次） */
    private PublicKey publicKey(String kid) {
        if (kid != null && jwksCache.containsKey(kid)) {
            return jwksCache.get(kid);
        }
        refreshJwks();
        PublicKey key = jwksCache.get(kid);
        if (key == null) {
            throw new BusinessException("JWKS 中不存在 kid=%s 的公钥", kid);
        }
        return key;
    }

    private void refreshJwks() {
        try {
            String body = restClient.get()
                    .uri(properties.getAuthCenter().getBaseUrl() + "/.well-known/jwks.json")
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
                PublicKey pub = KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(n, e));
                jwksCache.put(k.path("kid").asText(), pub);
            }
        } catch (Exception e) {
            throw new BusinessException("拉取 JWKS 失败: %s", e.getMessage());
        }
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

    /** 16 字节随机 state */
    private String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
