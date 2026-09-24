package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;

/**
 * fin_voucher_entry INSERT 参数（结账自动凭证分录行）。
 */
@Data
public class VoucherEntryInsertParam {

    /** 凭证 ID */
    private Long voucherId;

    /** 行号（从 1 起） */
    private Integer lineNo;

    /** 摘要 */
    private String summary;

    /** 科目 ID */
    private Long subjectId;

    /** 借方金额（单边，另一侧为 0） */
    private BigDecimal debitAmount;

    /** 贷方金额（单边，另一侧为 0） */
    private BigDecimal creditAmount;

    /** 操作人（审计） */
    private Long userId;
}
