package com.erp.report.dto.query;

import lombok.Data;

/**
 * 财务汇总查询参数。
 */
@Data
public class FinanceSummaryQuery {

    /** 会计期间 yyyy-MM（空 = 当前月） */
    private String period;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
