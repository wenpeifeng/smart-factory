package com.erp.report.dto.query;

import lombok.Data;

/**
 * 经营月报查询参数。
 */
@Data
public class MonthlySummaryQuery {

    /** 月份 yyyy-MM（默认当月） */
    private String month;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
