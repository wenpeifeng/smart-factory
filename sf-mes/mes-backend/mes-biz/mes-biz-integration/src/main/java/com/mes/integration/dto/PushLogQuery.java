package com.mes.integration.dto;

import lombok.Data;

/**
 * ERP 回传推送日志分页查询对象（对账用）。
 */
@Data
public class PushLogQuery {
    /** 页码，从 1 开始 */
    private long pageNum = 1;
    /** 每页条数 */
    private long pageSize = 10;
    /** 业务类型：WORK_REPORT/PICKING/RETURN/QUALITY */
    private String bizType;
    /** 状态：PENDING/SUCCESS/FAILED */
    private String status;
    /** 业务单据号（模糊） */
    private String bizNo;
}
