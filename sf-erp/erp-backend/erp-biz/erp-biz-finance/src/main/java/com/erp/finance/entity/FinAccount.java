package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 资金账户实体（表 fin_account）。
 * <p>
 * 现金 / 银行账户两类；当前余额由收付款核销在数据库内做原子累加
 * （条件 UPDATE 防并发超扣），应用层不做读-改-写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_account")
public class FinAccount extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 账户编号，全局唯一（如 CASH-001 / BANK-001） */
    private String accountNo;

    /** 账户名称（库存现金 / 工行基本户） */
    private String name;

    /** 账户类型：CASH-现金 BANK-银行 */
    private String type;

    /** 开户银行（银行账户填写） */
    private String bankName;

    /** 银行账号 */
    private String bankAccountNo;

    /** 期初余额 */
    private BigDecimal initialBalance;

    /** 当前余额（收付款原子累加的结果） */
    private BigDecimal currentBalance;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
