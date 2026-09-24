package com.mes.system.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解 —— 标注在 Controller 的写操作方法上，
 * 由 {@code OperLogAspect} 切面自动记录到 sys_oper_log。
 * <p>
 * 用法：{@code @OperLog(module = "用户管理", operation = "新增用户")}
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 功能模块名（如"用户管理"） */
    String module();

    /** 操作内容（如"新增用户"） */
    String operation();
}
