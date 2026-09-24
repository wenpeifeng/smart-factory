package com.erp.finance.dto.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 利润表查询参数。
 * <p>
 * period=yyyy-MM（默认当月，驱动「本月数+本年累计数」两列）；
 * from/to 显式传入时「本月数」列切换为 [from,to] 自定义区间；
 * bookId 可空（空=当前账套）；export=csv 返回附件。
 */
@Data
public class IncomeStatementQuery {

    /** 会计期间（yyyy-MM，默认当月） */
    private String period;

    /** 本期区间起（含，可空） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 本期区间止（含，可空） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** 账套 ID（可空=当前账套） */
    private Long bookId;

    /** 导出格式：csv */
    private String export;
}
