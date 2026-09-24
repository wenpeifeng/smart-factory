package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 收款核销明细实体（表 fin_receipt_item）。
 * <p>
 * 记录收款单对每一笔应收的本次核销额；单笔核销额不得超过该应收的
 * 未核销余额（amount - settled_amount），由条件 UPDATE 在数据库层兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receipt_item")
public class FinReceiptItem extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 收款单 ID（fin_receipt.id） */
    private Long receiptId;

    /** 应收单 ID（fin_receivable.id） */
    private Long receivableId;

    /** 本次核销金额 */
    private BigDecimal verifyAmount;
}
