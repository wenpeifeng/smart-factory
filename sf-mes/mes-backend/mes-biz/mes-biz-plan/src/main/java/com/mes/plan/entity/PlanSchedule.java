package com.mes.plan.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 排程时间片实体（表 plan_schedule，工序 x 工作中心甘特；仅手动拖拽，无自动 APS）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("plan_schedule")
public class PlanSchedule extends BaseEntity {
    /** 工单工序 ID（plan_wo_step.id，唯一） */
    private Long woStepId;
    /** 工单 ID（冗余） */
    private Long workorderId;
    /** 工作中心 ID（base_workcenter.id） */
    private Long workcenterId;
    /** 计划开始时间 */
    private LocalDateTime planStartTime;
    /** 计划结束时间 */
    private LocalDateTime planEndTime;
    /** 拖拽锁定标志：1 锁定（后续重排不动） */
    private Integer lockFlag;
}
