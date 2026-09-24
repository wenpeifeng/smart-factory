package com.erp.report.dto.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 销售毛利分析查询参数。
 */
@Data
public class SalesMarginQuery {

    /** 开始日期（含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 结束日期（不含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** 聚合维度：customer / material（默认） */
    private String groupBy;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
