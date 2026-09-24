package com.erp.system.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.framework.context.OrgContextHolder;
import com.erp.system.dto.auth.LoginForm;
import com.erp.system.dto.auth.LoginVO;
import com.erp.system.dto.auth.OrgVO;
import com.erp.system.dto.auth.UserContextVO;
import com.erp.system.dto.menu.MenuVO;
import com.erp.system.entity.SysUser;
import com.erp.system.event.LoginLogEvent;
import com.erp.system.mapper.OrgDirectoryMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserOrgMapper;
import com.erp.system.security.JwtTokenProvider;
import com.erp.system.security.SecurityUtils;
import com.erp.system.service.AuthService;
import com.erp.system.service.SysMenuService;
import com.erp.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证服务实现。
 * <p>
 * 登录防爆破：同一账号密码连错 5 次锁定 10 分钟（Redis 计数
 * {@code login:fail:{tenantId}:{username}}，TTL 10 分钟，登录成功即清除）。
 * <p>
 * v1.1 多租户/组织：按（租户, 账号）定位用户（租户间允许同名账号）；
 * 令牌签入 tenantId/orgId；响应附可切换组织列表（当前为全部启用组织，
 * 用户-组织绑定关系落地后按绑定过滤）。
 * <p>
 * 登录日志（sys_login_log）：登录成功/失败/锁定与登出经 Spring 事件
 * {@link LoginLogEvent} 交 LoginLogListener 落库，认证逻辑与日志解耦。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 登录失败计数键前缀（按租户+账号维度计数） */
    private static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";

    /** 连续失败锁定阈值 */
    private static final int MAX_FAIL_COUNT = 5;

    /** 锁定时长 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final SysUserMapper userMapper;
    private final SysUserService userService;
    private final SysMenuService menuService;
    private final OrgDirectoryMapper orgDirectoryMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    /** 登录日志走 Spring 事件解耦：认证主流程不感知日志表，落库失败不影响登录 */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public LoginVO login(LoginForm form) {
        long start = System.currentTimeMillis();
        String username = form.getUsername().trim();
        // 租户缺省为默认租户 1000（存量账号所在租户）
        Long tenantId = form.getTenantId() != null ? form.getTenantId() : OrgContextHolder.DEFAULT_TENANT_ID;
        String failKey = LOGIN_FAIL_KEY_PREFIX + tenantId + ":" + username;

        // 锁定检查：计数达到阈值直接拒绝（不消耗 BCrypt 校验成本）
        String failCount = redis.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= MAX_FAIL_COUNT) {
            String msg = "密码错误次数过多，账号已锁定，请 10 分钟后重试";
            // 登录日志：锁定期内的每次尝试都记录（撞库/暴力破解取证依据）
            publishLoginFail(tenantId, username, null, "账号锁定中：" + msg, start);
            throw new BusinessException(msg);
        }

        // 按（租户, 账号）定位用户：登录前无租户上下文，走禁用租户插件的专用查询
        SysUser user = userMapper.selectByUsernameAndTenant(username, tenantId);
        // 账号不存在与密码错误统一提示，避免暴露账号是否存在
        if (user == null || !passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                redis.expire(failKey, LOCK_DURATION);
            }
            long remaining = MAX_FAIL_COUNT - (count == null ? 1 : count);
            String msg = remaining > 0
                    ? "账号或密码错误，剩余尝试次数 " + remaining
                    : "密码错误次数过多，账号已锁定，请 10 分钟后重试";
            // 第 5 次失败即触发锁定，日志原因带"已锁定"字样，可直接按此检索锁定事件
            publishLoginFail(tenantId, username, user == null ? null : user.getId(), msg, start);
            throw new BusinessException(msg);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            String msg = "账号已停用，请联系管理员";
            publishLoginFail(tenantId, username, user.getId(), msg, start);
            throw new BusinessException(msg);
        }

        // 登录成功：清空失败计数，按用户档案的租户/主组织签发上下文令牌
        redis.delete(failKey);
        Long orgId = user.getOrgId() != null ? user.getOrgId() : OrgContextHolder.DEFAULT_ORG_ID;
        String token = tokenProvider.createToken(user.getId(), tenantId, orgId);
        eventPublisher.publishEvent(LoginLogEvent.loginSuccess(
                tenantId, username, user.getId(), System.currentTimeMillis() - start));
        return buildLoginVO(token, user.getId(), tenantId, orgId);
    }

    @Override
    public LoginVO switchOrg(Long orgId) {
        Long userId = requireCurrentUserId();
        Long tenantId = OrgContextHolder.getTenantId();
        // 目标组织必须在可切换列表内（启用状态 ∩ 用户绑定），防越权切换
        boolean allowed = listSwitchableOrgs(userId).stream().anyMatch(org -> org.getId().equals(orgId));
        if (!allowed) {
            throw BusinessException.ofKey("erp.system.org-not-switchable");
        }
        String token = tokenProvider.createToken(userId, tenantId, orgId);
        return buildLoginVO(token, userId, tenantId, orgId);
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        // 令牌可能已失效（过期/伪造），解析失败时无需任何处理
        if (tokenProvider.isValid(token)) {
            Long userId = tokenProvider.getUserId(token);
            tokenProvider.invalidate(userId, token);
            // 登出日志：账号名回查用户档案（此时 JwtAuthFilter 已落租户上下文，普通查询即可）
            SysUser user = userMapper.selectById(userId);
            eventPublisher.publishEvent(LoginLogEvent.logout(
                    tokenProvider.getTenantId(token), user == null ? String.valueOf(userId) : user.getUsername(), userId));
        }
    }

    @Override
    public UserContextVO getCurrentUser() {
        Long userId = requireCurrentUserId();
        UserContextVO context = new UserContextVO();
        context.setUser(userService.getDetail(userId));
        context.setRoles(userService.getRoleCodesByUserId(userId));
        context.setPerms(menuService.getPermsByUserId(userId));
        // 租户/组织为令牌上下文（切换组织后随新令牌变化），与用户档案解耦
        context.setTenantId(OrgContextHolder.getTenantId());
        context.setOrgId(OrgContextHolder.getOrgId());
        return context;
    }

    @Override
    public List<MenuVO> getCurrentMenus() {
        return menuService.buildUserMenuTree(requireCurrentUserId());
    }

    /** 组装登录/切换组织响应：令牌 + 用户信息 + 上下文 + 可切换组织（按用户绑定过滤） */
    private LoginVO buildLoginVO(String token, Long userId, Long tenantId, Long orgId) {
        return new LoginVO(token, userService.getDetail(userId), tenantId, orgId, listSwitchableOrgs(userId));
    }

    /** 发布登录失败事件（锁定/密码错误/停用统一入口；耗时从方法进入起算） */
    private void publishLoginFail(Long tenantId, String username, Long userId, String reason, long start) {
        eventPublisher.publishEvent(LoginLogEvent.loginFail(
                tenantId, username, userId, reason, System.currentTimeMillis() - start));
    }

    /**
     * 可切换组织列表（V1042 sys_user_org）：
     * <ul>
     *   <li>有绑定 → 绑定 ∩ 启用（停用组织自动剔除；全被停用时为空列表，不再回退全部，
     *       防止绑定用户借停用组织越权放开）；</li>
     *   <li>无绑定 → 全部启用组织（兼容存量用户/超管，行为与 v1.2 一致）。</li>
     * </ul>
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

    /** 取当前登录用户 ID，未登录（过滤器异常/白名单误配）按 401 处理 */
    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(com.erp.common.result.ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
