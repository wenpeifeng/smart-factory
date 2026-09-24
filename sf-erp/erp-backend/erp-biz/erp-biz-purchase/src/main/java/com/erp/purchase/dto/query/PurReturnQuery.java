package com.erp.purchase.dto.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 采购退货单分页查询条件。
 */
@Data
public class PurReturnQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 退货单号模糊搜索 */
    private String keyword;

    /** 供应商 ID */
    private Long supplierId;

    /** 状态：0草稿 10已审核 */
    private Integer status;

    /** 退货日期起（含） */
    private LocalDate dateBegin;

    /** 退货日期止（含） */
    private LocalDate dateEnd;
}
