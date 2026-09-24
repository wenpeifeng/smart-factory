package com.erp.report.dto.query;

import lombok.Data;

/**
 * 计件工资汇总查询参数。
 */
@Data
public class PiecewageQuery {

    /** 期间 yyyy-MM（默认当月；期间无工资表数据返回空列表） */
    private String period;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
