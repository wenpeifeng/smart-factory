package com.erp.invoice.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具。
 * <p>
 * 与框架层 MybatisPlusConfig 相同的解耦读取方式：JWT 过滤器（erp-system）认证成功后
 * 把用户 ID（Long）放入 SecurityContext 的 principal；此处以 {@code instanceof Long}
 * 读取，不引入 erp-system 类型（依赖方向只能向下）。未登录场景（定时任务等）返回 0。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 获取当前登录用户 ID（开票人 / 认证人字段使用）。
     *
     * @return 用户 ID；未登录返回 0（系统操作）
     */
    public static Long userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return 0L;
    }
}
