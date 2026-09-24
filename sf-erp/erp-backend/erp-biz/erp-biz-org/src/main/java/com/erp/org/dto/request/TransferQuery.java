package com.erp.org.dto.request;

import lombok.Data;

/**
 * 组织间调拨单分页查询（状态/工厂线路/单号关键词/创建日期区间）。
 */
@Data
public class TransferQuery {

    /** 页码（从 1 开始） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;

    /** 状态：0 草稿 1 已审核 2 已取消（空=全部） */
    private Integer status;

    /** 调出工厂 ID */
    private Long fromFactoryId;

    /** 调入工厂 ID */
    private Long toFactoryId;

    /** 单号/工厂名关键词 */
    private String keyword;

    /** 创建日期起（yyyy-MM-dd，含当日） */
    private String beginDate;

    /** 创建日期止（yyyy-MM-dd，含当日） */
    private String endDate;
}
