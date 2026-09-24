package com.erp.cost.dto.request;

import lombok.Data;

/**
 * 计价流水查询参数。
 */
@Data
public class CostFlowQuery {

    /** 期间 yyyyMM（精确匹配，空 = 全部） */
    private String period;

    /** 物料 ID */
    private Long materialId;

    /** 业务类型编码（CostBizType.code） */
    private String bizType;

    /** 关键词（匹配来源单号 / 物料编码 / 物料名称） */
    private String keyword;
}
