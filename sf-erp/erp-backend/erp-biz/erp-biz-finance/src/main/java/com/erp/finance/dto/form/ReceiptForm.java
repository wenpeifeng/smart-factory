package com.erp.finance.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 收款单（核销应收）提交表单。
 * <p>
 * 约束：收款金额必须等于明细核销额之和；明细必须属于同一客户；
 * 每笔核销额不得超过该应收的未核销余额（数据库条件 UPDATE 兜底）。
 *
 * @param customerId 客户 ID
 * @param accountId  收款资金账户 ID
 * @param bizDate    收款日期
 * @param amount     收款金额
 * @param remark     备注
 * @param items      核销明细（至少一条）
 */
public record ReceiptForm(
        @NotNull(message = "客户不能为空") Long customerId,
        @NotNull(message = "收款账户不能为空") Long accountId,
        @NotNull(message = "收款日期不能为空") LocalDate bizDate,
        @NotNull(message = "收款金额不能为空")
        @DecimalMin(value = "0.01", message = "收款金额必须大于0") BigDecimal amount,
        String remark,
        @NotEmpty(message = "核销明细不能为空")
        @Valid List<Item> items) {

    /**
     * 收款核销明细行。
     *
     * @param receivableId 应收单 ID
     * @param verifyAmount 本次核销额（&gt; 0，不得超过未核销余额）
     */
    public record Item(
            @NotNull(message = "应收单不能为空") Long receivableId,
            @NotNull(message = "核销金额不能为空")
            @DecimalMin(value = "0.01", message = "核销金额必须大于0") BigDecimal verifyAmount) {
    }
}
