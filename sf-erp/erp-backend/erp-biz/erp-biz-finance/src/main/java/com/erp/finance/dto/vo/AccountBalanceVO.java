package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 账户余额快照 VO（期初 + 收入 - 支出 应等于当前余额，可据此对账）。
 *
 * @param accountId      账户 ID
 * @param accountNo      账户编号
 * @param accountName    账户名称
 * @param initialBalance 期初余额
 * @param income         累计收入（收款核销流入）
 * @param expense        累计支出（付款核销流出）
 * @param currentBalance 当前余额
 */
public record AccountBalanceVO(Long accountId, String accountNo, String accountName,
                               BigDecimal initialBalance, BigDecimal income, BigDecimal expense,
                               BigDecimal currentBalance) {
}
