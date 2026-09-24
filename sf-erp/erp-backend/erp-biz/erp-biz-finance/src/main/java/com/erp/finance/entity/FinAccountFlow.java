package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账户流水实体（表 fin_account_flow）。
 * <p>
 * 每笔收款 / 付款核销自动写入一条流水，方向恒为收入或支出，
 * 并保存记账后的账户余额快照（balance_after），用于对账与余额追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_account_flow")
public class FinAccountFlow extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 资金账户 ID（fin_account.id） */
    private Long accountId;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 业务类型：RECEIPT-收款单 PAYMENT-付款单 */
    private String bizType;

    /** 业务单据 ID（收/付款单主键） */
    private Long bizId;

    /** 业务单号（SK/FK 开头） */
    private String bizNo;

    /** 收支方向：INCOME-收入 EXPENSE-支出 */
    private String direction;

    /** 发生金额（恒为正数） */
    private BigDecimal amount;

    /** 记账后账户余额快照 */
    private BigDecimal balanceAfter;

    /** 备注 */
    private String remark;
}
