package com.erp.inventory.dto.request;

import lombok.Data;

/**
 * 库存盘点单分页查询参数。
 */
@Data
public class CheckQuery {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 状态过滤：0盘点中 1已完成（null=全部） */
    private Integer status;

    /** 仓库 ID 精确过滤 */
    private Long warehouseId;

    /** 模糊搜索：单号 */
    private String keyword;
}
