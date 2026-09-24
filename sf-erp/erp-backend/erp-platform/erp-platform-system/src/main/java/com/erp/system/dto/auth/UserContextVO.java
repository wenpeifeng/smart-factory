package com.erp.system.dto.auth;

import com.erp.system.dto.user.UserVO;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户上下文（GET /auth/me 响应）：用户信息 + 角色编码 + 按钮权限标识。
 * <p>
 * 前端用 roles/perms 做按钮级显隐；均不含敏感字段。
 */
@Data
public class UserContextVO {

    /** 用户信息（不含口令） */
    private UserVO user;

    /** 角色编码列表（如 super_admin） */
    private List<String> roles;

    /** 按钮权限标识列表（如 system:user:add） */
    private List<String> perms;

    /** 当前租户 ID（令牌上下文） */
    private Long tenantId;

    /** 当前组织 ID（令牌上下文；切换组织后随新令牌变化） */
    private Long orgId;
}
