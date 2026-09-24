package com.erp.purchase.dto.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 采购入库单分页查询条件。
 */
@Data
public class PurInstockQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 入库单号模糊搜索 */
    private String keyword;

    /** 供应商 ID */
    private Long supplierId;

    /** 状态：0草稿 10已审核 */
    private Integer status;

    /** 入库日期起（含） */
    private LocalDate dateBegin;

    /** 入库日期止（含） */
    private LocalDate dateEnd;
}
