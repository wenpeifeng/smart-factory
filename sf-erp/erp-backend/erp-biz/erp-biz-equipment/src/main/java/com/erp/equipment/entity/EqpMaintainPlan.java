package com.erp.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设备保养计划实体。
 * <p>
 * 核心不变量：{@code nextDate = lastDate + cycleDays}，由服务端在
 * 创建/修改/执行时统一维护，客户端只提交 lastDate 与 cycleDays。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eqp_maintain_plan")
public class EqpMaintainPlan extends BaseEntity {

    /** 保养类型：一级保养 */
    public static final int TYPE_LEVEL1 = 1;
    /** 保养类型：二级保养 */
    public static final int TYPE_LEVEL2 = 2;
    /** 保养类型：大修 */
    public static final int TYPE_OVERHAUL = 3;

    /** 设备 ID（eqp_device.id） */
    private Long deviceId;

    /** 保养类型：1一级保养 2二级保养 3大修 */
    private Integer maintainType;

    /** 保养周期（天） */
    private Integer cycleDays;

    /** 上次保养日期 */
    private LocalDate lastDate;

    /** 下次保养日期（= 上次 + 周期，服务端维护） */
    private LocalDate nextDate;

    /** 备注（保养内容说明） */
    private String remark;
}
