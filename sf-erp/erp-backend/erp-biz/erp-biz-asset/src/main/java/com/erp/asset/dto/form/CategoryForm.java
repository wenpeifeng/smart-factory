package com.erp.asset.dto.form;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 资产类别新增/修改表单。
 *
 * @param id                              修改时由路径 id 覆盖，入参忽略
 * @param code                            类别编码（唯一）
 * @param name                            类别名称
 * @param depreciationMethod              折旧方法：AVG（当前仅实现平均年限法）
 * @param expectedLifeMonths              预计使用年限（月）
 * @param residualRate                    残值率（%），0~100
 * @param assetSubjectId                  资产科目 ID（fin_subject，入账借方）
 * @param accumulatedDepreciationSubjectId 累计折旧科目 ID（fin_subject）
 * @param depreciationExpenseSubjectId    折旧费用科目 ID（fin_subject）
 * @param remark                          备注
 */
public record CategoryForm(
        Long id,
        @NotBlank(message = "类别编码不能为空")
        @Size(max = 20, message = "类别编码长度不能超过 20")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "类别编码仅允许字母/数字/下划线/横线")
        String code,
        @NotBlank(message = "类别名称不能为空")
        @Size(max = 60, message = "类别名称长度不能超过 60")
        String name,
        @NotBlank(message = "折旧方法不能为空")
        @Pattern(regexp = "AVG", message = "折旧方法当前仅支持 AVG(平均年限法)")
        String depreciationMethod,
        @NotNull(message = "预计使用年限(月)不能为空")
        @Min(value = 1, message = "预计使用年限至少 1 个月")
        Integer expectedLifeMonths,
        @NotNull(message = "残值率不能为空")
        @DecimalMin(value = "0", message = "残值率不能小于 0")
        @DecimalMax(value = "100", message = "残值率不能超过 100")
        BigDecimal residualRate,
        @NotNull(message = "资产科目不能为空")
        Long assetSubjectId,
        @NotNull(message = "累计折旧科目不能为空")
        Long accumulatedDepreciationSubjectId,
        @NotNull(message = "折旧费用科目不能为空")
        Long depreciationExpenseSubjectId,
        @Size(max = 200, message = "备注长度不能超过 200")
        String remark) {
}
