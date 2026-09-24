package com.erp.purchase.dto.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 采购订单分页查询条件。
 *
 * @param pageNum  页码（从 1 开始）
 * @param pageSize 每页条数
 * @param keyword  单号模糊搜索
 * @param supplierId 供应商过滤
 * @param status   状态过滤
 * @param dateBegin 单据日期起（含）
 * @param dateEnd   单据日期止（含）
 */
@Data
public class PurOrderQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 单号模糊搜索 */
    private String keyword;

    /** 供应商 ID */
    private Long supplierId;

    /** 状态：0草稿 10已审核 20部分入库 30已完成 90已关闭 */
    private Integer status;

    /** 单据日期起（含），ISO 格式 yyyy-MM-dd */
    private LocalDate dateBegin;

    /** 单据日期止（含），ISO 格式 yyyy-MM-dd */
    private LocalDate dateEnd;
}
