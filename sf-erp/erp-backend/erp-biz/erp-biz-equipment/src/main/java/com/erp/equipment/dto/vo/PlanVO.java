package com.erp.equipment.dto.vo;

import com.erp.equipment.entity.EqpMaintainPlan;
import lombok.Data;

import java.time.LocalDate;

/**
 * 保养计划 VO（分页与到期清单共用，含设备冗余信息 + 到期标记）。
 */
@Data
public class PlanVO {

    /** 计划 ID */
    private Long id;

    /** 设备 ID */
    private Long deviceId;

    /** 设备编号（JOIN eqp_device） */
    private String deviceCode;

    /** 设备名称（JOIN eqp_device） */
    private String deviceName;

    /** 保养类型：1一级保养 2二级保养 3大修 */
    private Integer maintainType;

    /** 保养类型中文 */
    private String maintainTypeLabel;

    /** 保养周期（天） */
    private Integer cycleDays;

    /** 上次保养日期 */
    private LocalDate lastDate;

    /** 下次保养日期 */
    private LocalDate nextDate;

    /** 距到期天数（负数=已逾期 |天数| 天，0=今天到期） */
    private Long daysLeft;

    /** 是否已到期（next_date &lt; 今天） */
    private Boolean overdue;

    /** 备注 */
    private String remark;

    /** 保养类型码 → 中文 */
    public static String typeLabelOf(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case EqpMaintainPlan.TYPE_LEVEL1 -> "一级保养";
            case EqpMaintainPlan.TYPE_LEVEL2 -> "二级保养";
            case EqpMaintainPlan.TYPE_OVERHAUL -> "大修";
            default -> "未知";
        };
    }
}
