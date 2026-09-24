package com.erp.purchase.dto.query;

import lombok.Data;

/**
 * 采购暂估应付单分页查询条件。
 */
@Data
public class PurEstimationQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词：暂估单号/入库单号/发票号模糊 */
    private String keyword;

    /** 供应商 ID */
    private Long supplierId;

    /** 状态：0暂估中 10已冲回 20部分冲回 */
    private Integer status;

    /** 暂估期间 yyyyMM */
    private String period;
}
