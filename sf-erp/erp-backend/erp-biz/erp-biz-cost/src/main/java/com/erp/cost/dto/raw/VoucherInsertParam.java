package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * fin_voucher INSERT 参数（结账自动凭证）。
 * <p>
 * 以单对象传参便于 useGeneratedKeys 回写自增 id。
 */
@Data
public class VoucherInsertParam {

    /** 自增 ID（回写） */
    private Long id;

    /** 凭证号（PZ+yyyyMMdd+4位流水） */
    private String voucherNo;

    /** 凭证日期（结账期间最后一日） */
    private LocalDate voucherDate;

    /** 凭证字（转） */
    private String word;

    /** 借方合计 */
    private BigDecimal totalDebit;

    /** 贷方合计 */
    private BigDecimal totalCredit;

    /** 状态（DRAFT） */
    private String status;

    /** 摘要备注 */
    private String remark;

    /** 操作人（审计） */
    private Long userId;
}
