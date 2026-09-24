package com.erp.equipment.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备台账新增/修改表单（Entity 不直接作入参，防 mass assignment）。
 */
@Data
public class DeviceForm {

    /** 设备编号（留空自动生成 SB+日期+流水；修改时不可改码） */
    @Size(max = 40, message = "设备编号长度不能超过 40")
    private String code;

    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称长度不能超过 100")
    private String name;

    /** 规格型号 */
    @Size(max = 100, message = "型号长度不能超过 100")
    private String model;

    /** 制造商 */
    @Size(max = 100, message = "制造商长度不能超过 100")
    private String manufacturer;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 购置原值 */
    @NotNull(message = "购置原值不能为空")
    @DecimalMin(value = "0", message = "购置原值不能为负")
    @Digits(integer = 16, fraction = 2, message = "购置原值最多 16 位整数 2 位小数")
    private BigDecimal originalValue;

    /** 所在车间（文本） */
    @Size(max = 100, message = "车间长度不能超过 100")
    private String workshop;

    /** 初始状态：1运行 2停机（新建不允许直接建维修中/报废） */
    @NotNull(message = "初始状态不能为空")
    @Min(value = 1, message = "初始状态不合法")
    @Max(value = 2, message = "新设备只能是运行或停机状态")
    private Integer status;

    /** 责任人 */
    @Size(max = 50, message = "责任人长度不能超过 50")
    private String responsiblePerson;

    /** 点检周期（天），空=不点检 */
    @Min(value = 1, message = "点检周期至少 1 天")
    private Integer checkPeriodDays;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
