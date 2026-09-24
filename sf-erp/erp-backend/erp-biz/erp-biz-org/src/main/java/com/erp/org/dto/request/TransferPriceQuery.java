package com.erp.org.dto.request;

import lombok.Data;

/**
 * 调拨价目分页查询（物料 / 调出工厂 / 调入工厂 / 状态）。
 */
@Data
public class TransferPriceQuery {

    /** 页码（从 1 开始） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;

    /** 物料 ID */
    private Long materialId;

    /** 调出工厂 ID */
    private Long fromFactoryId;

    /** 调入工厂 ID */
    private Long toFactoryId;

    /** 状态：1 启用 0 停用（空=全部） */
    private Integer status;
}
