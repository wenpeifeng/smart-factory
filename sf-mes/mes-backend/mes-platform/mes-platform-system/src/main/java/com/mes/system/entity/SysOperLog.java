package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志实体（表 sys_oper_log）。
 * <p>
 * 由 {@code @OperLog} 注解 + AOP 切面（OperLogAspect）在写操作前后自动落库，
 * 记录"谁在什么时间对什么模块做了什么、参数、耗时与结果"。
 * 只增不改，deleted 列按建表规范保留恒为 0。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class SysOperLog extends BaseEntity {

    /** 功能模块（注解 module 属性，如"用户管理"） */
    private String module;

    /** 操作内容（注解 operation 属性，如"新增用户"） */
    private String operation;

    /** 执行方法（类全名#方法名） */
    private String method;

    /** 请求方式（GET/POST/PUT/DELETE） */
    private String requestMethod;

    /** 请求地址 */
    private String requestUrl;

    /** 请求参数（JSON 串，超长截断到 2000 字符） */
    private String requestParams;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人账号 */
    private String operatorName;

    /** 客户端 IP */
    private String ip;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 结果：1-成功 0-失败 */
    private Integer status;

    /** 失败原因（异常信息，截断到 500 字符） */
    private String errorMsg;
}
