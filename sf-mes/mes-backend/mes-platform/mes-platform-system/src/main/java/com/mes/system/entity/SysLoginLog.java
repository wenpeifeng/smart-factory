package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录日志实体（表 sys_login_log，V1031）。
 * <p>
 * 记录登录链路四类事件：登录成功 / 登录失败（密码错误、账号停用）/ 账号锁定 / 登出。
 * 由 {@code AuthServiceImpl} 发布 {@link com.mes.system.event.LoginLogEvent}，
 * {@code LoginLogListener} 监听落库 —— 认证主流程不直接依赖日志表，日志失败不影响登录。
 * 只增不改，deleted 列按建表规范保留恒为 0。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class SysLoginLog extends BaseEntity {

    /** 事件：登录 */
    public static final String EVENT_LOGIN = "LOGIN";

    /** 事件：登出 */
    public static final String EVENT_LOGOUT = "LOGOUT";

    /** 结果：成功 */
    public static final int STATUS_SUCCESS = 1;

    /** 结果：失败 */
    public static final int STATUS_FAIL = 0;

    /** 登录账号（失败时也有值，便于统计被撞库账号） */
    private String username;

    /** 用户 ID（账号可定位时有值，账号不存在为 null） */
    private Long userId;

    /** 事件：LOGIN / LOGOUT */
    private String event;

    /** 结果：1-成功 0-失败 */
    private Integer status;

    /** 失败原因（密码错误剩余次数 / 账号已锁定 / 账号停用等） */
    private String errorMsg;

    /** 客户端 IP */
    private String ip;

    /** 浏览器 UA（截断到 500 字符） */
    private String userAgent;

    /** 耗时（毫秒，登录含 BCrypt 校验） */
    private Long costMs;
}
