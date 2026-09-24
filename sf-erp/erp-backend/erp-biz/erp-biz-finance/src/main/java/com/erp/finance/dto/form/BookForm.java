package com.erp.finance.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 账套新增/修改表单。
 *
 * @param name         账套名称
 * @param periodPolicy 会计期间政策（NATURAL_MONTH，null 默认自然月）
 * @param currency     本位币 ISO 代码（3 位大写字母，null 默认 CNY）
 * @param status       状态：1-启用 0-停用（null 默认启用）
 * @param remark       备注
 */
public record BookForm(
        @NotBlank(message = "账套名称不能为空")
        @Size(max = 60, message = "账套名称最长 60 字符") String name,
        @Pattern(regexp = "^$|NATURAL_MONTH", message = "会计期间政策当前仅支持 NATURAL_MONTH（自然月）") String periodPolicy,
        @Pattern(regexp = "^$|[A-Z]{3}", message = "本位币为 3 位大写 ISO 代码，如 CNY") String currency,
        Integer status,
        @Size(max = 200, message = "备注最长 200 字符") String remark) {
}
