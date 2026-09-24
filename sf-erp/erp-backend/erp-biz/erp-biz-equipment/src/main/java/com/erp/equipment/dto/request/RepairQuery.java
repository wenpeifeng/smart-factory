package com.erp.equipment.dto.request;

import lombok.Data;

/**
 * 维修工单分页查询参数。
 */
@Data
public class RepairQuery {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 工单状态：0待维修 1维修中 2已修复 3已关闭（null=全部） */
    private Integer status;

    /** 设备 ID 精确过滤 */
    private Long deviceId;

    /** 模糊搜索：工单号 / 设备编码 / 设备名称 */
    private String keyword;
}
