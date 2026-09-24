package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinAccount;

import java.math.BigDecimal;

/**
 * 资金账户 VO。
 *
 * @param id             账户 ID
 * @param accountNo      账户编号
 * @param name           账户名称
 * @param type           账户类型：CASH / BANK
 * @param bankName       开户银行
 * @param bankAccountNo  银行账号
 * @param initialBalance 期初余额
 * @param currentBalance 当前余额
 * @param status         状态：1-启用 0-停用
 * @param remark         备注
 */
public record AccountVO(Long id, String accountNo, String name, String type, String bankName,
                        String bankAccountNo, BigDecimal initialBalance, BigDecimal currentBalance,
                        Integer status, String remark) {

    /** 实体转 VO */
    public static AccountVO from(FinAccount a) {
        return new AccountVO(a.getId(), a.getAccountNo(), a.getName(), a.getType(),
                a.getBankName(), a.getBankAccountNo(), a.getInitialBalance(),
                a.getCurrentBalance(), a.getStatus(), a.getRemark());
    }
}
