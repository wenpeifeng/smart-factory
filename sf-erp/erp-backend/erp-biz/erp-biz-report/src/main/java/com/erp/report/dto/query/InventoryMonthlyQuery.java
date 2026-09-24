package com.erp.report.dto.query;

import lombok.Data;

/**
 * 收发存月报查询参数。
 */
@Data
public class InventoryMonthlyQuery {

    /** 月份 yyyy-MM（默认当月；内部转为 cost_flow.period 的 yyyyMM） */
    private String month;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
