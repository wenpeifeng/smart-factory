package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收款单主实体（表 fin_receipt）。
 * <p>
 * 一张收款单选择同一客户的多笔应收分配核销（明细见
 * {@link FinReceiptItem}）；保存后原子回写应收的已核销金额与状态，
 * 并增加资金账户余额、记录账户流水，全过程在同一事务内。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receipt")
public class FinReceipt extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 收款单号（SK + yyyyMMdd + 4 位流水，Redis 自增生成） */
    private String receiptNo;

    /** 客户 ID */
    private Long customerId;

    /** 收款资金账户 ID */
    private Long accountId;

    /** 收款日期 */
    private LocalDate bizDate;

    /** 收款金额（必须等于明细核销额之和） */
    private BigDecimal amount;

    /** 已核销金额（当前模型下恒等于收款金额） */
    private BigDecimal verifiedAmount;

    /** 备注 */
    private String remark;
}
