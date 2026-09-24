package com.erp.report.dto.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 采购明细报表查询参数。
 * <p>
 * from/to 均可空：同时为空表示全量；区间为半开 [from, to)。
 */
@Data
public class PurchaseDetailQuery {

    /** 开始日期（含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 结束日期（不含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** 供应商 ID（可空 = 全部供应商） */
    private Long supplierId;

    /** 聚合维度：material（默认）/ supplier / month */
    private String groupBy;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
