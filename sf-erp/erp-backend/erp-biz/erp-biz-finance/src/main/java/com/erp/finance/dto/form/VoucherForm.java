package com.erp.finance.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 记账凭证新增/修改表单。
 * <p>
 * 服务端强校验：分录至少 2 行；每行借/贷单边（一侧 &gt; 0 另一侧为 0）；
 * 借贷合计必须精确相等（BigDecimal.compareTo，非 equals）。
 *
 * @param voucherDate     凭证日期
 * @param word            凭证字：记 / 收 / 付 / 转
 * @param attachmentCount 附件张数
 * @param remark          备注
 * @param entries         分录行（至少 2 行）
 */
public record VoucherForm(
        @NotNull(message = "凭证日期不能为空") LocalDate voucherDate,
        @NotBlank(message = "凭证字不能为空") String word,
        Integer attachmentCount,
        String remark,
        @NotEmpty(message = "凭证分录不能为空")
        @Valid List<Entry> entries) {

    /**
     * 凭证分录行。
     *
     * @param summary      摘要
     * @param subjectId    科目 ID（必须为启用状态的末级科目）
     * @param debitAmount  借方金额（与贷方互斥；null 按 0 处理）
     * @param creditAmount 贷方金额（与借方互斥；null 按 0 处理）
     */
    public record Entry(
            @NotBlank(message = "摘要不能为空") String summary,
            @NotNull(message = "科目不能为空") Long subjectId,
            @DecimalMin(value = "0", message = "借方金额不能为负") BigDecimal debitAmount,
            @DecimalMin(value = "0", message = "贷方金额不能为负") BigDecimal creditAmount) {
    }
}
