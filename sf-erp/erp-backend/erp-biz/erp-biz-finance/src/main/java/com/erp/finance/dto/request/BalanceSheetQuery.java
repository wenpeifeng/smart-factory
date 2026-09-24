package com.erp.finance.dto.request;

import lombok.Data;

/**
 * 资产负债表查询参数。
 * <p>
 * period=yyyy-MM（默认当月）；bookId 可空（空=取 X-Book-Id 请求头解析的当前账套，
 * 显式传入时优先生效——供报表中心 CSV 导出等无法携带请求头的场景使用）；
 * export=csv 时返回 CSV 附件。
 */
@Data
public class BalanceSheetQuery {

    /** 会计期间（yyyy-MM，默认当月） */
    private String period;

    /** 账套 ID（可空=当前账套） */
    private Long bookId;

    /** 导出格式：csv */
    private String export;
}
