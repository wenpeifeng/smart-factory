package com.erp.finance.dto.form;

import jakarta.validation.constraints.NotBlank;

/**
 * 会计科目新增/修改表单。
 *
 * @param code      科目编码（全局唯一）
 * @param name      科目名称
 * @param parentId  上级科目 ID，null/0 表示一级科目
 * @param category  科目类别：ASSET / LIABILITY / EQUITY / COST / PROFIT_LOSS
 * @param direction 余额方向：DEBIT / CREDIT
 * @param status    状态：1-启用 0-停用（null 默认启用）
 * @param remark    备注
 */
public record SubjectForm(
        @NotBlank(message = "科目编码不能为空") String code,
        @NotBlank(message = "科目名称不能为空") String name,
        Long parentId,
        @NotBlank(message = "科目类别不能为空") String category,
        @NotBlank(message = "余额方向不能为空") String direction,
        Integer status,
        String remark) {
}
