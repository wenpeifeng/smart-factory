package com.erp.report.dto.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 材料损耗分析查询参数。
 */
@Data
public class MaterialLossQuery {

    /** 开始日期（含，按领料单创建时间 0 点），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 结束日期（不含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
