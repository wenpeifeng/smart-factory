package com.erp.framework.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具 —— 全系统获取当前登录用户的唯一入口。
 * <p>
 * 约定：JWT 过滤器（erp-system 的 JwtAuthFilter）向 SecurityContext 放入的
 * principal 为用户 ID（Long）。未登录场景（定时任务/系统初始化）返回 0 表示"系统"。
 * 公共封装，业务模块一律通过本类取用户，禁止各自手写 SecurityContextHolder 解析。
 */
public final class SecurityUtils {

    /** 系统操作者（无登录上下文时使用） */
    public static final Long SYSTEM_USER_ID = 0L;

    private SecurityUtils() {
    }

    /** 当前登录用户 ID；未登录返回 0（系统操作） */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return SYSTEM_USER_ID;
    }

    /** 是否已登录（真实用户） */
    public static boolean isLoggedIn() {
        return getCurrentUserId() != SYSTEM_USER_ID;
    }
}
