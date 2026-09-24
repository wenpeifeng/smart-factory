package com.erp.system.service;

import com.erp.system.dto.auth.LoginForm;
import com.erp.system.dto.auth.LoginVO;
import com.erp.system.dto.auth.UserContextVO;
import com.erp.system.dto.menu.MenuVO;

import java.util.List;

/**
 * 认证服务：登录 / 登出 / 当前用户上下文 / 当前用户菜单树。
 */
public interface AuthService {

    /**
     * 账号密码登录。
     * <p>
     * 安全策略：BCrypt 校验；同一账号 10 分钟内密码连错 5 次锁定
     * （Redis 计数 {@code login:fail:{tenantId}:{username}}，登录成功清零）。
     * <p>
     * v1.1：表单可携带 tenantId（缺省默认租户 1000），令牌签入 tenant/org 上下文，
     * 响应附当前组织与可切换组织列表。
     *
     * @param form 登录表单
     * @return 令牌 + 用户信息 + 租户/组织上下文
     */
    LoginVO login(LoginForm form);

    /**
     * 切换当前组织：校验目标组织可用后重签携带新 orgId 的令牌。
     * <p>
     * 旧令牌在有效期内仍持有旧组织上下文（多标签页兼容），前端应及时替换本地令牌。
     *
     * @param orgId 目标组织 ID（org_factory.id）
     * @return 新令牌 + 用户信息 + 租户/组织上下文
     */
    LoginVO switchOrg(Long orgId);

    /**
     * 登出：删除 Redis 在线会话并拉黑令牌（剩余有效期）。
     *
     * @param token 当前请求的 JWT（从 Authorization 头解析）
     */
    void logout(String token);

    /**
     * 当前登录用户上下文（用户信息 + 角色编码 + 按钮权限）。
     *
     * @return 用户上下文
     */
    UserContextVO getCurrentUser();

    /**
     * 当前用户可见的路由菜单树。
     *
     * @return 菜单树
     */
    List<MenuVO> getCurrentMenus();
}
