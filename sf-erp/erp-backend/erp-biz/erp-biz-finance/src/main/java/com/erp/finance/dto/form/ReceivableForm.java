package com.erp.finance.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收单手工新增表单（正常来源是销售模块出库自动 INSERT，此处提供手工补录能力）。
 *
 * @param customerId 客户 ID
 * @param bizNo      来源业务单号
 * @param amount     应收金额（必须 &gt; 0）
 * @param bizDate    业务日期
 * @param dueDate    到期日
 * @param remark     备注
 */
public record ReceivableForm(
        @NotNull(message = "客户不能为空") Long customerId,
        @NotBlank(message = "来源单号不能为空") String bizNo,
        @NotNull(message = "应收金额不能为空")
        @DecimalMin(value = "0.01", message = "应收金额必须大于0") BigDecimal amount,
        LocalDate bizDate,
        LocalDate dueDate,
        String remark) {
}
