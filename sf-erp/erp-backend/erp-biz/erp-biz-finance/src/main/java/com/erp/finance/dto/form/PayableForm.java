package com.erp.finance.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付单手工新增表单（正常来源是采购模块入库自动 INSERT，此处提供手工补录能力）。
 *
 * @param supplierId 供应商 ID
 * @param bizNo      来源业务单号
 * @param amount     应付金额（必须 &gt; 0）
 * @param bizDate    业务日期
 * @param dueDate    到期日
 * @param remark     备注
 */
public record PayableForm(
        @NotNull(message = "供应商不能为空") Long supplierId,
        @NotBlank(message = "来源单号不能为空") String bizNo,
        @NotNull(message = "应付金额不能为空")
        @DecimalMin(value = "0.01", message = "应付金额必须大于0") BigDecimal amount,
        LocalDate bizDate,
        LocalDate dueDate,
        String remark) {
}
