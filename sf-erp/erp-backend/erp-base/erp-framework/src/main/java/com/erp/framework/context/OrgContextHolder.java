package com.erp.framework.context;

/**
 * 租户/组织上下文（ThreadLocal）—— 多租户与数据权限两大隔离维度的请求级载体。
 * <p>
 * 写入方：erp-platform-system 的 JwtAuthFilter（从 JWT claims + 用户数据范围加载），
 * 请求结束（filter finally）调用 {@link #clear()} 清理，防止线程复用串上下文。
 * <p>
 * 读取方：erp-framework 的租户插件（{@code TenantLineInnerInterceptor} 取 tenantId）、
 * 审计填充器（tenantId/orgId 写入）与数据权限拦截器（orgId + dataScope 注入 SQL）。
 * <p>
 * 放置在 framework 而非 system 的原因：MybatisPlusConfig / 拦截器位于 framework，
 * 依赖方向只能向下（common ← framework ← system），system 写入、framework 读取。
 * <p>
 * 未登录/后台任务（无上下文）时：tenantId 默认 1000（默认租户）、orgId 默认 1（总厂）、
 * dataScope 为 null（数据权限拦截器跳过注入，等同 ALL）——与库列默认值一致。
 */
public final class OrgContextHolder {

    /** 默认租户 ID（存量数据归属，与各表 tenant_id 列 DEFAULT 一致） */
    public static final long DEFAULT_TENANT_ID = 1000L;

    /** 默认组织 ID（业务数据归属总厂，与隔离表 org_id 列 DEFAULT 一致） */
    public static final long DEFAULT_ORG_ID = 1L;

    /** 组织上下文快照：tenantId/orgId 来自令牌，dataScope/deptId 来自用户档案 */
    public record OrgContext(Long tenantId, Long orgId, String dataScope, Long userId, Long deptId) {
    }

    private static final ThreadLocal<OrgContext> HOLDER = new ThreadLocal<>();

    private OrgContextHolder() {
    }

    /** 写入当前请求的组织上下文（JwtAuthFilter 认证成功后调用） */
    public static void set(OrgContext context) {
        HOLDER.set(context);
    }

    /** 读取原始上下文（可能为 null：未登录或非 HTTP 线程） */
    public static OrgContext get() {
        return HOLDER.get();
    }

    /** 清理上下文（请求结束必须调用，防止线程池串数据） */
    public static void clear() {
        HOLDER.remove();
    }

    /** 当前租户 ID；无上下文返回默认租户 1000 */
    public static Long getTenantId() {
        OrgContext ctx = HOLDER.get();
        return ctx != null && ctx.tenantId != null ? ctx.tenantId : DEFAULT_TENANT_ID;
    }

    /** 当前组织 ID；无上下文返回默认组织 1（总厂） */
    public static Long getOrgId() {
        OrgContext ctx = HOLDER.get();
        return ctx != null && ctx.orgId != null ? ctx.orgId : DEFAULT_ORG_ID;
    }

    /** 当前用户数据范围（枚举名字符串）；无上下文返回 null（拦截器跳过注入） */
    public static String getDataScope() {
        OrgContext ctx = HOLDER.get();
        return ctx != null ? ctx.dataScope() : null;
    }

    /** 当前用户部门 ID（sys_user.dept_id，DEPT_ONLY 档注入用）；无上下文/未设部门返回 null */
    public static Long getDeptId() {
        OrgContext ctx = HOLDER.get();
        return ctx != null ? ctx.deptId() : null;
    }
}
