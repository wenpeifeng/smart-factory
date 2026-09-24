package com.mes.system.event;

import com.mes.system.entity.SysLoginLog;
import com.mes.system.mapper.SysLoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录日志监听器：接收 {@link LoginLogEvent} → 落库 sys_login_log + 打一行审计日志。
 * <p>
 * 约束：
 * <ul>
 *   <li>同步监听（不加 @Async）：需要在请求线程读取 IP / UA，且登录本身低频，同步落库开销可忽略；</li>
 *   <li>落库失败只 warn 不上抛（日志属旁路能力，绝不能让用户因日志表故障登录失败）；</li>
 *   <li>tenant_id 显式取事件租户（登录前无令牌上下文，审计填充器只会填默认租户）；
 *       org_id 固定 0（共享日志表，无组织语义，与 sys_oper_log 一致）；</li>
 *   <li>审计日志行统一前缀 [登录日志]，便于在 JSON 日志中按 message 前缀检索。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogListener {

    /** user_agent 列宽 */
    private static final int MAX_UA_LENGTH = 500;

    /** error_msg 列宽 */
    private static final int MAX_ERROR_LENGTH = 500;

    private final SysLoginLogMapper loginLogMapper;

    @EventListener
    public void onLoginLog(LoginLogEvent event) {
        String ip = null;
        String userAgent = null;
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            ip = resolveClientIp(request);
            userAgent = truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH);
        }

        // 审计日志：登录成败一律 INFO 可见（失败是安全关注点，用 WARN 便于告警检索）
        if (event.success()) {
            log.info("[登录日志] 事件={} 账号={} userId={} 租户={} ip={} 耗时={}ms",
                    event.event(), event.username(), event.userId(), event.tenantId(), ip, event.costMs());
        } else {
            log.warn("[登录日志] 事件={} 账号={} 租户={} ip={} 失败原因={} 耗时={}ms",
                    event.event(), event.username(), event.tenantId(), ip, event.errorMsg(), event.costMs());
        }

        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setUsername(event.username());
            entity.setUserId(event.userId());
            entity.setEvent(event.event());
            entity.setStatus(event.success() ? SysLoginLog.STATUS_SUCCESS : SysLoginLog.STATUS_FAIL);
            entity.setErrorMsg(truncate(event.errorMsg(), MAX_ERROR_LENGTH));
            entity.setIp(ip);
            entity.setUserAgent(userAgent);
            entity.setCostMs(event.costMs());
            entity.setTenantId(event.tenantId());
            entity.setOrgId(0L);
            // 登录成功/登出时 create_by 记录本人（此时 SecurityContext 尚无认证，填充器会填 0）
            if (event.userId() != null) {
                entity.setCreateBy(event.userId());
                entity.setUpdateBy(event.userId());
            }
            loginLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[登录日志] 落库失败 账号={} 事件={}: {}", event.username(), event.event(), e.getMessage());
        }
    }

    /**
     * 客户端 IP：优先反向代理头（Nginx 同源转发场景 remoteAddr 恒为 127.0.0.1），
     * X-Forwarded-For 多级代理取第一个。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
