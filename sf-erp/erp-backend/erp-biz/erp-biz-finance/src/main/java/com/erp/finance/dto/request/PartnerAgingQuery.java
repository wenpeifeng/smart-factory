package com.erp.finance.dto.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 应收/应付账龄分析查询参数。
 * <p>
 * asOf=yyyy-MM-dd（账龄计算时点，默认今天）；bookId 可空（空=当前账套）；
 * export=csv 返回附件。
 */
@Data
public class PartnerAgingQuery {

    /** 账龄计算时点（默认今天） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate asOf;

    /** 账套 ID（可空=当前账套） */
    private Long bookId;

    /** 导出格式：csv */
    private String export;
}
