package com.erp.report.dto.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 报工工时统计查询参数。
 */
@Data
public class WorkhoursQuery {

    /** 开始日期（含，按报工时间 0 点），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 结束日期（不含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** 工作中心 ID（可空 = 全部，经 mrp_wo_step 关联过滤） */
    private Long workCenterId;

    /** 聚合维度：operation（默认）/ employee */
    private String groupBy;

    /** export=csv 时返回 text/csv 附件，否则返回 JSON */
    private String export;
}
