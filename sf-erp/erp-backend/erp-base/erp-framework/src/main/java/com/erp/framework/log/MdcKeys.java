package com.erp.framework.log;

/**
 * 日志 MDC 键名约定 —— 全系统日志上下文字段的唯一事实来源。
 * <p>
 * 写入方：{@link TraceIdFilter}（traceId，请求最先执行）与 erp-platform-system 的
 * JwtAuthFilter（tenantId/orgId/userId，令牌解析成功后）；
 * 读取方：logback-spring.xml（控制台 pattern 的 %X{traceId} 与 JSON encoder 的
 * includeMdcKeyName）。改键名必须同步 logback-spring.xml 与 docs/日志规范.md。
 * <p>
 * 放在 framework 而非 system：JwtAuthFilter（system）依赖本常量，依赖方向向下合法；
 * 反之 framework 不得引用 system。
 */
public final class MdcKeys {

    /** 请求链路 ID（16 位十六进制；上游传入 X-Trace-Id 时沿用） */
    public static final String TRACE_ID = "traceId";

    /** 租户 ID（来自 JWT claims） */
    public static final String TENANT_ID = "tenantId";

    /** 组织 ID（来自 JWT claims，切换组织后随新令牌变化） */
    public static final String ORG_ID = "orgId";

    /** 登录用户 ID（JWT principal） */
    public static final String USER_ID = "userId";

    /** 登录用户部门 ID（sys_user.dept_id，来自用户档案；DEPT_ONLY 数据范围问题定位用） */
    public static final String DEPT_ID = "deptId";

    /** traceId 的 HTTP 头名（请求可携带用于链路透传；响应必回显便于前端/运维按 ID 检索日志） */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private MdcKeys() {
    }
}
