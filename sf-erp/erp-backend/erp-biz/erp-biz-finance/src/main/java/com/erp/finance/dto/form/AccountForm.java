package com.erp.finance.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 资金账户新增/修改表单。
 *
 * @param accountNo      账户编号（全局唯一）
 * @param name           账户名称
 * @param type           账户类型：CASH / BANK
 * @param bankName       开户银行
 * @param bankAccountNo  银行账号
 * @param initialBalance 期初余额（修改时差额同步调整当前余额，不允许调为负余额）
 * @param status         状态：1-启用 0-停用（null 默认启用）
 * @param remark         备注
 */
public record AccountForm(
        @NotBlank(message = "账户编号不能为空") String accountNo,
        @NotBlank(message = "账户名称不能为空") String name,
        @NotBlank(message = "账户类型不能为空") String type,
        String bankName,
        String bankAccountNo,
        @NotNull(message = "期初余额不能为空")
        @DecimalMin(value = "0", message = "期初余额不能为负") BigDecimal initialBalance,
        Integer status,
        String remark) {
}
