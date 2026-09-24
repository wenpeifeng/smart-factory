package com.erp.report.dto.query;

import lombok.Data;

/**
 * 呆滞物料分析查询参数。
 */
@Data
public class SluggishQuery {

    /** 呆滞天数阈值（默认 90，无出库流水天数 ≥ days 判定呆滞） */
    private Integer days;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
