package com.erp.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌提供者（生成 / 解析 / 校验 / Redis 会话管理）。
 * <p>
 * 会话策略（JWT + Redis 双层控制，支持强制下线）：
 * <ul>
 *   <li>签发：HS256 签名，subject 存用户 ID，jti 存随机 ID；</li>
 *   <li>在线态：登录时写 Redis 键 {@code token:{userId}:{token}}（TTL=令牌有效期），
 *       过滤器校验"签名有效 + 键存在"才放行 —— 键被删除即令牌失效；</li>
 *   <li>黑名单：登出时写 {@code auth:blacklist:{token}}（TTL=剩余有效期），
 *       双保险防止极端情况下旧键复活。</li>
 * </ul>
 * 密钥与有效期支持配置项 erp.jwt.secret / erp.jwt.expire-minutes 覆盖（默认值仅供开发）。
 */
@Component
public class JwtTokenProvider {

    /** 在线令牌键前缀：token:{userId}:{token} */
    public static final String TOKEN_KEY_PREFIX = "token:";

    /** 黑名单键前缀：auth:blacklist:{token} */
    public static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    /** 默认租户（存量数据归属，与 OrgContextHolder.DEFAULT_TENANT_ID 一致） */
    private static final long DEFAULT_TENANT_ID = 1000L;

    /** 默认组织（总厂，与 OrgContextHolder.DEFAULT_ORG_ID 一致） */
    private static final long DEFAULT_ORG_ID = 1L;

    /** 租户 claim 名 */
    private static final String CLAIM_TENANT_ID = "tenantId";

    /** 当前组织 claim 名 */
    private static final String CLAIM_ORG_ID = "orgId";

    private final SecretKey secretKey;

    /** 令牌有效期（毫秒） */
    private final long expireMillis;

    private final StringRedisTemplate redis;

    public JwtTokenProvider(@Value("${erp.jwt.secret:erp-dev-jwt-secret-key-change-me-in-production-0123456789abcdef}")
                            String secret,
                            @Value("${erp.jwt.expire-minutes:480}") long expireMinutes,
                            StringRedisTemplate redis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = Duration.ofMinutes(expireMinutes).toMillis();
        this.redis = redis;
    }

    /**
     * 为用户签发令牌并写入 Redis 在线会话（兼容入口：默认租户 1000 / 总厂 1）。
     *
     * @param userId 用户 ID
     * @return 已签名的 JWT 字符串
     */
    public String createToken(Long userId) {
        return createToken(userId, null, null);
    }

    /**
     * 签发携带租户/组织上下文的令牌并写入 Redis 在线会话。
     * <p>
     * tenantId/orgId 作为自定义 claims 随令牌流动：切换组织即重签令牌
     * （旧令牌在有效期内仍持有旧组织上下文，前端应及时替换本地令牌）。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID（null 按默认 1000 处理）
     * @param orgId    当前组织 ID（null 按总厂 1 处理）
     * @return 已签名的 JWT 字符串
     */
    public String createToken(Long userId, Long tenantId, Long orgId) {
        Date now = new Date();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TENANT_ID, tenantId == null ? DEFAULT_TENANT_ID : tenantId)
                .claim(CLAIM_ORG_ID, orgId == null ? DEFAULT_ORG_ID : orgId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(secretKey)
                .compact();
        redis.opsForValue().set(onlineKey(userId, token), "1", Duration.ofMillis(expireMillis));
        return token;
    }

    /**
     * 解析令牌中的用户 ID。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     * @throws JwtException 令牌无效或已过期时抛出，由调用方决定 401
     */
    public Long getUserId(String token) {
        Claims claims = parse(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 解析令牌中的租户 ID。
     *
     * @param token JWT 字符串
     * @return 租户 ID；旧版令牌无该 claim 时返回默认租户 1000
     * @throws JwtException 令牌无效或已过期时抛出
     */
    public Long getTenantId(String token) {
        return numberClaim(parse(token), CLAIM_TENANT_ID, DEFAULT_TENANT_ID);
    }

    /**
     * 解析令牌中的当前组织 ID。
     *
     * @param token JWT 字符串
     * @return 组织 ID；旧版令牌无该 claim 时返回总厂 1
     * @throws JwtException 令牌无效或已过期时抛出
     */
    public Long getOrgId(String token) {
        return numberClaim(parse(token), CLAIM_ORG_ID, DEFAULT_ORG_ID);
    }

    /**
     * 校验令牌综合有效性：签名未过期 + 未被拉黑 + Redis 在线键仍存在。
     *
     * @param token JWT 字符串
     * @return true 表示可用（userId 可通过 {@link #getUserId} 获取）
     */
    public boolean isValid(String token) {
        try {
            Long userId = getUserId(token);
            if (Boolean.TRUE.equals(redis.hasKey(blacklistKey(token)))) {
                return false;
            }
            return Boolean.TRUE.equals(redis.hasKey(onlineKey(userId, token)));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 登出：删除在线会话键并写入黑名单（TTL 取令牌剩余有效期，自动清理）。
     *
     * @param userId 用户 ID
     * @param token  JWT 字符串
     */
    public void invalidate(Long userId, String token) {
        long remain = remainMillis(token);
        if (remain <= 0) {
            return;
        }
        redis.delete(onlineKey(userId, token));
        redis.opsForValue().set(blacklistKey(token), "1", Duration.ofMillis(remain));
    }

    /**
     * 计算令牌剩余有效毫秒数（解析失败按 0 处理）。
     *
     * @param token JWT 字符串
     * @return 剩余毫秒，已过期或非法返回 0
     */
    public long remainMillis(String token) {
        try {
            Date expiration = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload().getExpiration();
            return Math.max(0, expiration.getTime() - System.currentTimeMillis());
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    /** 拼接在线会话键：token:{userId}:{token} */
    private String onlineKey(Long userId, String token) {
        return TOKEN_KEY_PREFIX + userId + ":" + token;
    }

    /** 拼接黑名单键：auth:blacklist:{token} */
    private String blacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + token;
    }

    /** 解析并验签令牌，返回 claims 载荷 */
    private Claims parse(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload();
    }

    /** 读取数值 claim（JSON 反序列化可能是 Integer/Long，统一转 Long；缺失用默认值） */
    private Long numberClaim(Claims claims, String name, long defaultValue) {
        Object value = claims.get(name);
        return value instanceof Number number ? number.longValue() : defaultValue;
    }
}
