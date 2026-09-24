package com.erp.system.dto.log;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询条件。
 */
@Data
public class OperLogQuery {

    /** 功能模块（精确，如"用户管理"） */
    private String module;

    /** 操作人账号（模糊） */
    private String operatorName;

    /** 结果过滤：1-成功 0-失败 */
    private Integer status;

    /** 起始时间（含） */
    private LocalDateTime beginTime;

    /** 截止时间（含） */
    private LocalDateTime endTime;

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;
}
