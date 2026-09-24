package com.erp.workflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录人工具（workflow 模块内自持，不依赖 erp-system）。
 * <p>
 * 与 erp-framework 的 MybatisPlusConfig 审计填充器同一取法：JWT 过滤器
 * 认证成功后 principal 即用户 ID（Long）。本模块只依赖 framework，
 * 因此按约定复制该读取逻辑（依赖方向只能向下，不得 import erp-system）。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID；未登录（定时任务/白名单接口）返回 null
     */
    public static Long getUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /**
     * 获取当前登录用户 ID，未登录抛业务异常。
     * 审批/待办等接口必须登录，调用方应在入口统一拦截。
     *
     * @return 用户 ID
     * @throws com.erp.common.exception.BusinessException 未登录时
     */
    public static Long requireUserId() {
        Long userId = getUserIdOrNull();
        if (userId == null) {
            throw new com.erp.common.exception.BusinessException("未获取到登录用户上下文");
        }
        return userId;
    }
}
