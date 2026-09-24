package com.erp.cost.dto.request;

import lombok.Data;

/**
 * 生产订单成本归集列表查询参数。
 */
@Data
public class WorkOrderQuery {

    /** 关键词（匹配工单号 / 成品物料编码 / 成品物料名称） */
    private String keyword;

    /** 订单状态（DRAFT/RELEASED/PRODUCING/FINISHED/CLOSED） */
    private String status;
}
