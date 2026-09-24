package com.erp.workflow.dto.query;

import lombok.Data;

/**
 * 流程实例分页查询条件（我发起的流程列表）。
 */
@Data
public class WfInstanceQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词（单号模糊） */
    private String keyword;

    /** 单据类型 */
    private String bizType;

    /** 实例状态：RUNNING/AGREED/REJECTED/CANCELLED */
    private String status;
}
