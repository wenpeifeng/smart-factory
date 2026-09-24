package com.mes.system.security;

import com.mes.framework.context.OrgContextHolder;
import com.mes.framework.log.MdcKeys;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.service.SysMenuService;
import com.mes.system.service.SysUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器。
 * <p>
 * 每个请求执行一次：从 Authorization: Bearer {token} 解析用户，
 * 令牌综合有效（签名 + Redis 在线态 + 黑名单，见 {@link JwtTokenProvider#isValid}）
 * 时把认证信息放入 SecurityContext，并加载租户/组织上下文。
 * <p>
 * 约定：principal 直接放用户 ID（Long）。mes-framework 的审计填充器
 * （MybatisPlusConfig）以 {@code instanceof Long} 读取，从而避免
 * framework 反向依赖 system 模块 —— 改动 principal 类型前必须同步评估两侧。
 * <p>
 * 租户/组织上下文（v1.1 多租户改造）：
 * <ul>
 *   <li>tenantId/orgId 取自令牌 claims（登录/切换组织时签入），写入
 *       {@link OrgContextHolder}，供租户插件、审计填充、数据权限拦截器读取；</li>
 *   <li>dataScope（用户数据范围）与角色编码来自用户档案，Redis 缓存 10 分钟
 *       （与权限码缓存同策略：变更最迟 10 分钟生效）；</li>
 *   <li>角色编码以 {@code ROLE_} 前缀追加进 authorities，其中 ROLE_super_admin
 *       用于数据权限拦截器放行超管（见 DataScopeInnerInterceptor）；</li>
 *   <li>请求结束 finally 清理 OrgContextHolder，防止线程池串上下文。</li>
 * </ul>
 * Bean 名固定为 {@code jwtAuthFilter}，mes-framework 的 SecurityConfig
 * 按名查找挂载到过滤链（同样为了避免反向编译依赖）。
 */
@Component("jwtAuthFilter")
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    /** 权限码 Redis 缓存键前缀：角色/菜单变更后最长 PERMS_CACHE_TTL 内自然失效 */
    private static final String PERMS_CACHE_KEY = "auth:perms:";
    /** 数据范围缓存键前缀：用户档案 data_scope 变更后最长 10 分钟生效 */
    private static final String SCOPE_CACHE_KEY = "auth:scope:";
    /** 用户部门缓存键前缀：sys_user.dept_id 变更后最长 10 分钟生效（DEPT_ONLY 档注入用） */
    private static final String DEPT_CACHE_KEY = "auth:dept:";
    /** 角色编码缓存键前缀 */
    private static final String ROLES_CACHE_KEY = "auth:roles:";
    private static final Duration PERMS_CACHE_TTL = Duration.ofMinutes(10);

    private final JwtTokenProvider tokenProvider;
    private final SysMenuService menuService;
    private final SysUserService userService;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        try {
            // 已有认证（理论上不会出现，防御性判断）或无令牌时不处理，交给授权规则判定
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (tokenProvider.isValid(token)) {
                    Long userId = tokenProvider.getUserId(token);
                    Long tenantId = tokenProvider.getTenantId(token);
                    Long orgId = tokenProvider.getOrgId(token);

                    // 先落租户/组织上下文（dataScope/deptId 暂空），后续用户档案/角色/权限查询
                    // 即被租户插件按正确租户过滤 —— 顺序不可颠倒
                    OrgContextHolder.set(new OrgContextHolder.OrgContext(tenantId, orgId, null, userId, null));
                    // 日志 MDC：本请求后续所有日志（业务审计 log / SQL / 异常）自动携带租户/组织/用户，
                    // 与 TraceIdFilter 写入的 traceId 一起构成日志检索四元组（见 docs/日志规范.md）
                    MDC.put(MdcKeys.TENANT_ID, String.valueOf(tenantId));
                    MDC.put(MdcKeys.ORG_ID, String.valueOf(orgId));
                    MDC.put(MdcKeys.USER_ID, String.valueOf(userId));
                    Long deptId = loadDeptId(userId);
                    if (deptId != null) {
                        MDC.put(MdcKeys.DEPT_ID, String.valueOf(deptId));
                    }

                    // authorities = 按钮权限码 + 角色编码（ROLE_ 前缀，供数据权限超管放行判定）
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    loadPermissions(userId).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                    loadRoleCodes(userId).forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 补齐数据范围与部门（均来自用户档案，带 10 分钟缓存）
                    OrgContextHolder.set(new OrgContextHolder.OrgContext(
                            tenantId, orgId, loadDataScope(userId), userId, deptId));
                }
                // 令牌存在但无效：不设置认证，SecurityConfig 的 401 入口统一返回 Result JSON
            }
            filterChain.doFilter(request, response);
        } finally {
            // 无论请求成败必须清理，防止容器线程复用导致租户/组织上下文串数据
            OrgContextHolder.clear();
            // 只移除本过滤器写入的键（traceId 由 TraceIdFilter 自管，不可 MDC.clear 误清）
            MDC.remove(MdcKeys.TENANT_ID);
            MDC.remove(MdcKeys.ORG_ID);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.DEPT_ID);
        }
    }

    /**
     * 加载用户权限码（带 Redis 缓存）。
     * <p>
     * 权限码来源：角色 → 菜单(按钮) → sys_menu.perms，超管角色自动持有全部按钮权限
     * （见 SysMenuServiceImpl#getPermsByUserId）。缓存 10 分钟：换取零 DB 开销，
     * 权限变更最迟 10 分钟生效；调角色/菜单后如需立即生效可删除对应缓存键。
     */
    private List<String> loadPermissions(Long userId) {
        String cacheKey = PERMS_CACHE_KEY + userId;
        List<String> cached = redisTemplate.opsForList().range(cacheKey, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<String> perms = menuService.getPermsByUserId(userId);
        if (!perms.isEmpty()) {
            redisTemplate.delete(cacheKey);
            redisTemplate.opsForList().rightPushAll(cacheKey, perms);
            redisTemplate.expire(cacheKey, PERMS_CACHE_TTL);
        }
        return perms;
    }

    /**
     * 加载用户数据范围（sys_user.data_scope，带 10 分钟缓存）。
     * <p>
     * 缓存为空串时表示"用户档案存在但无范围"（理论不应发生，DB 默认 ALL）。
     */
    private String loadDataScope(Long userId) {
        String cached = redisTemplate.opsForValue().get(SCOPE_CACHE_KEY + userId);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        SysUser user = userMapper.selectById(userId);
        String scope = user != null && user.getDataScope() != null ? user.getDataScope() : "ALL";
        redisTemplate.opsForValue().set(SCOPE_CACHE_KEY + userId, scope, PERMS_CACHE_TTL);
        return scope;
    }

    /**
     * 加载用户部门 ID（sys_user.dept_id，带 10 分钟缓存）。
     * <p>
     * DEPT_ONLY 数据范围注入用（设计文档第 3 章）；用户未挂部门返回 null，
     * 拦截器对该用户退化为组织级过滤（OR 条件缺 dept 列的表同此退化）。
     * 缓存空串 = 已查过且无部门，避免未设部门的用户每请求回表。
     */
    private Long loadDeptId(Long userId) {
        String cached = redisTemplate.opsForValue().get(DEPT_CACHE_KEY + userId);
        if (cached != null) {
            return cached.isEmpty() ? null : Long.valueOf(cached);
        }
        SysUser user = userMapper.selectById(userId);
        String dept = user != null && user.getDeptId() != null ? user.getDeptId().toString() : "";
        redisTemplate.opsForValue().set(DEPT_CACHE_KEY + userId, dept, PERMS_CACHE_TTL);
        return dept.isEmpty() ? null : Long.valueOf(dept);
    }

    /**
     * 加载用户角色编码（带 10 分钟缓存；超管角色用于数据权限放行）。
     */
    private List<String> loadRoleCodes(Long userId) {
        String cacheKey = ROLES_CACHE_KEY + userId;
        List<String> cached = redisTemplate.opsForList().range(cacheKey, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<String> roles = userService.getRoleCodesByUserId(userId);
        if (!roles.isEmpty()) {
            redisTemplate.delete(cacheKey);
            redisTemplate.opsForList().rightPushAll(cacheKey, roles);
            redisTemplate.expire(cacheKey, PERMS_CACHE_TTL);
        }
        return roles;
    }

    /**
     * 从请求头解析 Bearer 令牌。
     *
     * @param request 当前请求
     * @return 令牌字符串；头缺失或格式不符返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
