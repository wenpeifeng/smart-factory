package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 付款核销明细实体（表 fin_payment_item）。
 * <p>
 * 记录付款单对每一笔应付的本次核销额；单笔核销额不得超过该应付的
 * 未核销余额，由条件 UPDATE 在数据库层兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment_item")
public class FinPaymentItem extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 付款单 ID（fin_payment.id） */
    private Long paymentId;

    /** 应付单 ID（fin_payable.id） */
    private Long payableId;

    /** 本次核销金额 */
    private BigDecimal verifyAmount;
}
