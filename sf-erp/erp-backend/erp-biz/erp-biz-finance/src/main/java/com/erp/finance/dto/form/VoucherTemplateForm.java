package com.erp.finance.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 凭证模板新增/修改表单（V521）。
 *
 * @param code       模板编码
 * @param name       模板名称
 * @param bizType    单据类型（八类，uk 约束每类一条模板）
 * @param entryRules 分录规则 JSON 数组：[{"side":"DEBIT|CREDIT","subjectCode":"1405|@account","amountExpr":"order_total|detail_tax_excl_total|tax_total|detail_cost_total","summary":"... {单号} ..."}]
 * @param enabled    启用：1/0（0=停用，扫描器不再生成）
 * @param priority   优先级（预留）
 * @param remark     备注
 */
public record VoucherTemplateForm(
        @NotBlank(message = "模板编码不能为空") String code,
        @NotBlank(message = "模板名称不能为空") String name,
        @NotBlank(message = "单据类型不能为空") String bizType,
        @NotBlank(message = "分录规则不能为空") String entryRules,
        @NotNull(message = "启用状态不能为空") Integer enabled,
        Integer priority,
        String remark) {
}
