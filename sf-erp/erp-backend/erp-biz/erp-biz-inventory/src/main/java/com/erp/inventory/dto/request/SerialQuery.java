package com.erp.inventory.dto.request;

import lombok.Data;

/**
 * 序列号查询对象（V422）。
 */
@Data
public class SerialQuery {

    /** 页码（1 起） */
    private long pageNum = 1;

    /** 页大小 */
    private long pageSize = 10;

    /** 序列号（精确或前缀匹配） */
    private String serialNo;

    /** 物料 ID（反查：该物料的序列号清单） */
    private Long materialId;

    /** 状态（IN_STOCK/OUT/SCRAP） */
    private String status;

    /** 来源/出库单号（模糊） */
    private String docNo;
}
