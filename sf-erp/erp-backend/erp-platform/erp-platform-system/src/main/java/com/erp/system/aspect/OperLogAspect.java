package com.erp.system.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.system.annotation.OperLog;
import com.erp.system.entity.SysOperLog;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysOperLogMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 操作日志切面：拦截标注 {@link OperLog} 的方法，环绕记录请求与结果到 sys_oper_log。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>日志落库失败只打 warn 不影响业务（finally 中 try-catch 兜底）；</li>
 *   <li>业务异常原样上抛（由全局异常处理器转 Result），日志先记录失败状态；</li>
 *   <li>请求参数过滤掉 Servlet 对象与文件后 JSON 序列化，超长截断到 2000 字符。</li>
 * </ul>
 */
@Slf4j
@Aspect
// 顺序先于方法级安全拦截（Spring Security AuthorizationInterceptorsOrder.PRE_AUTHORIZE=200）：
// 保证权限不足被拒（403）的写操作尝试也落 sys_oper_log（status=0，留痕审计）。
@Order(50)
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** request_params 列宽 */
    private static final int MAX_PARAMS_LENGTH = 2000;

    /** error_msg 列宽 */
    private static final int MAX_ERROR_LENGTH = 500;

    private final SysOperLogMapper operLogMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    /**
     * 环绕通知：统一记录成功/失败与耗时。
     *
     * @param joinPoint 切点（Controller 方法）
     * @param operLog   注解元数据（module/operation）
     * @return 业务方法返回值
     * @throws Throwable 业务方法抛出的原始异常（原样上抛）
     */
    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        int status = 1;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            status = 0;
            errorMsg = throwable.getMessage();
            throw throwable;
        } finally {
            saveLog(joinPoint, operLog, status, errorMsg, System.currentTimeMillis() - start);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperLog operLog,
                         int status, String errorMsg, long costMs) {
        try {
            SysOperLog logEntity = new SysOperLog();
            logEntity.setModule(operLog.module());
            logEntity.setOperation(operLog.operation());
            logEntity.setMethod(joinPoint.getSignature().getDeclaringTypeName()
                    + "#" + joinPoint.getSignature().getName());
            logEntity.setStatus(status);
            logEntity.setErrorMsg(truncate(errorMsg, MAX_ERROR_LENGTH));
            logEntity.setCostMs(costMs);

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                logEntity.setRequestMethod(request.getMethod());
                logEntity.setRequestUrl(request.getRequestURI());
                logEntity.setIp(request.getRemoteAddr());
                logEntity.setRequestParams(serializeArgs(joinPoint.getArgs()));
            }
            Long operatorId = SecurityUtils.getCurrentUserId();
            logEntity.setOperatorId(operatorId);
            if (operatorId != null) {
                SysUser operator = userMapper.selectById(operatorId);
                logEntity.setOperatorName(operator == null ? null : operator.getUsername());
            }
            operLogMapper.insert(logEntity);
        } catch (Exception e) {
            // 日志属旁路能力：任何失败不阻断业务
            log.warn("[OperLog] 操作日志落库失败 module={} operation={}: {}",
                    operLog.module(), operLog.operation(), e.getMessage());
        }
    }

    /** 参数序列化：过滤 Servlet/文件等不可序列化对象，失败降级为 toString */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Object[] filtered = Arrays.stream(args)
                    .map(arg -> arg instanceof HttpServletRequest
                            || arg instanceof jakarta.servlet.http.HttpServletResponse
                            || arg instanceof MultipartFile ? null : arg)
                    .toArray();
            return truncate(objectMapper.writeValueAsString(filtered), MAX_PARAMS_LENGTH);
        } catch (Exception e) {
            return truncate(Arrays.stream(args)
                    .map(arg -> arg == null ? "null" : arg.getClass().getSimpleName())
                    .collect(Collectors.joining(",")), MAX_PARAMS_LENGTH);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
