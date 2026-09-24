package com.erp.workflow.dto.query;

import lombok.Data;

/**
 * 流程定义分页查询条件。
 */
@Data
public class WfDefinitionQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词（流程编码/名称模糊） */
    private String keyword;

    /** 适用单据类型 */
    private String bizType;

    /** 状态：1启用 0停用 */
    private Integer status;
}
