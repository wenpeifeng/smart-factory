package com.mes.system.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具 —— 供本模块（及其他模块，仅依赖 mes-system 时）读取当前登录人。
 * <p>
 * 与 {@link JwtAuthFilter} 的约定一致：principal 为用户 ID（Long）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID；未登录（定时任务、系统初始化、白名单接口）返回 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /**
     * 获取当前登录用户 ID，未登录时返回给定默认值。
     *
     * @param defaultUserId 未登录时的兜底值（通常传 0L 表示系统操作）
     * @return 用户 ID 或默认值
     */
    public static Long getCurrentUserIdOrDefault(Long defaultUserId) {
        Long userId = getCurrentUserId();
        return userId != null ? userId : defaultUserId;
    }
}
