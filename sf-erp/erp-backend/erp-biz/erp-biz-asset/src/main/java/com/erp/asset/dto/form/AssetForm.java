package com.erp.asset.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产卡片新增/修改表单。
 * <p>
 * 财务字段（原值/残值/年限/类别/入账日期）仅在卡片<b>未计提折旧</b>时可修改
 * （一旦生成折旧或处置记录，改财务字段会破坏凭证与账实一致，Service 层强制拒绝）。
 *
 * @param id                 修改时由路径 id 覆盖，入参忽略
 * @param name               资产名称
 * @param categoryId         类别 ID
 * @param spec               规格型号
 * @param supplierId         供应商 ID（可空）
 * @param location           存放地点
 * @param deptId             使用部门 ID（可空；折旧费用按部门汇总）
 * @param keeper             责任人
 * @param originalValue      原值（新增必填 &gt;0；修改受计提锁定限制）
 * @param residualValue      残值（可空；新增为空时按 原值×类别残值率 计算）
 * @param expectedLifeMonths 预计使用年限月（可空；新增为空时取类别默认）
 * @param accountDate        入账日期（可空；新增为空时取当天）
 * @param status             状态（修改时可传 IN_USE/IDLE 互转；处置必须走 dispose 接口）
 * @param remark             备注
 */
public record AssetForm(
        Long id,
        @NotBlank(message = "资产名称不能为空")
        @Size(max = 100, message = "资产名称长度不能超过 100")
        String name,
        @NotNull(message = "资产类别不能为空")
        Long categoryId,
        @Size(max = 200, message = "规格型号长度不能超过 200")
        String spec,
        Long supplierId,
        @Size(max = 100, message = "存放地点长度不能超过 100")
        String location,
        Long deptId,
        @Size(max = 50, message = "责任人长度不能超过 50")
        String keeper,
        @DecimalMin(value = "0.01", message = "原值必须大于 0")
        BigDecimal originalValue,
        @DecimalMin(value = "0", message = "残值不能小于 0")
        BigDecimal residualValue,
        @Min(value = 1, message = "预计使用年限至少 1 个月")
        Integer expectedLifeMonths,
        LocalDate accountDate,
        @Pattern(regexp = "IN_USE|IDLE", message = "状态仅允许 IN_USE(在用) 或 IDLE(闲置)")
        String status,
        @Size(max = 200, message = "备注长度不能超过 200")
        String remark) {
}
