package com.erp.system.dto.log;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志分页查询条件。
 */
@Data
public class LoginLogQuery {

    /** 登录账号（模糊） */
    private String username;

    /** 事件过滤：LOGIN / LOGOUT */
    private String event;

    /** 结果过滤：1-成功 0-失败 */
    private Integer status;

    /** 客户端 IP（精确） */
    private String ip;

    /** 起始时间（含） */
    private LocalDateTime beginTime;

    /** 截止时间（含） */
    private LocalDateTime endTime;

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;
}
