package com.erp.equipment.dto.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 保养计划新增/修改表单。
 * <p>
 * nextDate 由服务端计算（lastDate + cycleDays），不接收客户端提交值。
 */
@Data
public class PlanForm {

    /** 设备 ID */
    @NotNull(message = "请选择设备")
    private Long deviceId;

    /** 保养类型：1一级保养 2二级保养 3大修 */
    @NotNull(message = "请选择保养类型")
    @Min(value = 1, message = "保养类型不合法")
    @Max(value = 3, message = "保养类型不合法")
    private Integer maintainType;

    /** 保养周期（天） */
    @NotNull(message = "保养周期不能为空")
    @Min(value = 1, message = "保养周期至少 1 天")
    private Integer cycleDays;

    /** 上次保养日期（新计划可填最近一次保养日，作为周期起点） */
    private LocalDate lastDate;

    /** 备注（保养内容说明） */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
