package com.erp.equipment.dto.request;

import lombok.Data;

/**
 * 保养计划分页查询参数。
 */
@Data
public class PlanQuery {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 设备 ID 精确过滤 */
    private Long deviceId;

    /** true=只看已到期+7 天内到期 */
    private Boolean dueOnly;
}
