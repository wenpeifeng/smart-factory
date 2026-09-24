package com.erp.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备保养执行记录 —— 每执行一次保养落一条（plans/{id}/execute）。
 * <p>
 * 与计划的关系：record 冗余 device_id，按设备回溯历史时无需再 JOIN 计划表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eqp_maintain_record")
public class EqpMaintainRecord extends BaseEntity {

    /** 保养计划 ID（eqp_maintain_plan.id） */
    private Long planId;

    /** 设备 ID（冗余自计划，便于按设备查保养历史） */
    private Long deviceId;

    /** 执行时间 */
    private LocalDateTime executeTime;

    /** 执行人（文本） */
    private String executor;

    /** 保养内容 */
    private String content;
}
