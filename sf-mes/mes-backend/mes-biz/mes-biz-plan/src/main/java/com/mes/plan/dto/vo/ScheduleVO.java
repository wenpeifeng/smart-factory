package com.mes.plan.dto.vo;

import com.mes.plan.entity.PlanSchedule;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 排程时间片 VO。
 */
@Data
public class ScheduleVO {
    private Long id;
    private Long woStepId;
    private Long workorderId;
    private Long workcenterId;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private Integer lockFlag;

    /**
     * 实体转 VO。
     *
     * @param e 排程实体
     * @return VO
     */
    public static ScheduleVO from(PlanSchedule e) {
        ScheduleVO vo = new ScheduleVO();
        vo.setId(e.getId());
        vo.setWoStepId(e.getWoStepId());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWorkcenterId(e.getWorkcenterId());
        vo.setPlanStartTime(e.getPlanStartTime());
        vo.setPlanEndTime(e.getPlanEndTime());
        vo.setLockFlag(e.getLockFlag());
        return vo;
    }
}
