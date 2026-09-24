package com.erp.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 凭证分录表本地映射（表 fin_voucher_entry，erp-finance 为表 owner）。
 * <p>
 * 每行分录 = 摘要 + 科目（末级）+ 借/贷<b>单边</b>金额，行号在凭证内从 1 连续编号；
 * 字段级镜像 fin_voucher_entry（见 {@link FinVoucher} 的协作约定说明）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher_entry")
public class FinVoucherEntry extends BaseEntity {

    /** 凭证 ID（fin_voucher.id） */
    private Long voucherId;

    /** 分录行号（同一凭证内从 1 开始连续） */
    private Integer lineNo;

    /** 摘要 */
    private String summary;

    /** 科目 ID（必须为末级科目） */
    private Long subjectId;

    /** 借方金额（与贷方互斥，有且仅有一侧 &gt; 0） */
    private BigDecimal debitAmount;

    /** 贷方金额（与借方互斥） */
    private BigDecimal creditAmount;
}
