package com.mes.equipment.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备保养/维修记录实体（表 eqp_maintain）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("eqp_maintain")
public class EqpMaintain extends BaseEntity {
    /** 保养/维修单号（BY+yyyyMMdd+4位流水） */
    private String maintainNo;
    /** 设备 ID */
    private Long deviceId;
    /** 类型：PM 保养 / REPAIR 维修 */
    private String type;
    /** 计划日期 */
    private LocalDate planDate;
    /** 执行人 */
    private String maintainer;
    /** 耗时（分钟） */
    private Integer durationMinutes;
    /** 状态：PLAN 计划 / DONE 完成 */
    private String status;
    /** 保养/维修内容 */
    private String content;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** 备注 */
    private String remark;
}
