package com.mes.system.event;

/**
 * 登录链路事件 —— 认证服务与登录日志之间的解耦载体。
 * <p>
 * 发布方：{@code AuthServiceImpl}（登录成功 / 密码错误 / 账号锁定 / 账号停用 / 登出）；
 * 监听方：{@code LoginLogListener} 落库 sys_login_log 并打审计日志。
 * 认证主流程只依赖 Spring 的 ApplicationEventPublisher，不感知日志表结构；
 * 日志落库失败不影响登录结果（监听器内部兜底）。
 * <p>
 * IP / User-Agent 不在事件中携带，由监听器在同一请求线程从 RequestContextHolder 读取
 * （监听器必须保持同步执行，改异步须先在发布侧补齐这两个字段）。
 *
 * @param tenantId 登录目标租户（表单指定，缺省默认租户）
 * @param username 登录账号（失败时也有值）
 * @param userId   用户 ID（成功/登出有值，失败为 null）
 * @param event    事件类型：LOGIN / LOGOUT（见 SysLoginLog 常量）
 * @param success  是否成功
 * @param errorMsg 失败原因（成功为 null）
 * @param costMs   耗时（毫秒）
 */
public record LoginLogEvent(Long tenantId, String username, Long userId, String event,
                            boolean success, String errorMsg, long costMs) {

    /** 登录成功 */
    public static LoginLogEvent loginSuccess(Long tenantId, String username, Long userId, long costMs) {
        return new LoginLogEvent(tenantId, username, userId, "LOGIN", true, null, costMs);
    }

    /** 登录失败（密码错误 / 锁定 / 停用，原因写入 errorMsg） */
    public static LoginLogEvent loginFail(Long tenantId, String username, Long userId, String reason, long costMs) {
        return new LoginLogEvent(tenantId, username, userId, "LOGIN", false, reason, costMs);
    }

    /** 登出 */
    public static LoginLogEvent logout(Long tenantId, String username, Long userId) {
        return new LoginLogEvent(tenantId, username, userId, "LOGOUT", true, null, 0L);
    }
}
