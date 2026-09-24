package com.erp.report.dto.query;

import lombok.Data;

/**
 * 费用分析查询参数。
 */
@Data
public class ExpenseQuery {

    /** 期间 yyyy-MM（默认当月） */
    private String period;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
