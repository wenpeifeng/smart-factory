package com.mes.workflow.dto.query;

import lombok.Data;

/**
 * 任务查询条件（我的待办/已办共用，页码页距由前端传）。
 */
@Data
public class WfTaskQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词（单号模糊） */
    private String keyword;

    /** 单据类型 */
    private String bizType;
}
