package com.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.plan.dto.vo.ScheduleVO;
import com.mes.plan.entity.PlanSchedule;
import com.mes.plan.entity.PlanWoStep;
import com.mes.plan.mapper.PlanScheduleMapper;
import com.mes.plan.mapper.PlanWoStepMapper;
import com.mes.plan.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工序排程服务实现（按 wo_step_id upsert 时间片，同步回写工序行计划时间）。
 */
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final PlanScheduleMapper scheduleMapper;
    private final PlanWoStepMapper woStepMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Long woStepId, Long workcenterId, LocalDateTime start, LocalDateTime end, Integer lockFlag) {
        PlanWoStep step = woStepMapper.selectById(woStepId);
        if (step == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单工序不存在: %d".formatted(woStepId));
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException("结束时间不能早于开始时间");
        }
        PlanSchedule schedule = scheduleMapper.selectOne(Wrappers.lambdaQuery(PlanSchedule.class)
                .eq(PlanSchedule::getWoStepId, woStepId).last("LIMIT 1"));
        if (schedule == null) {
            schedule = new PlanSchedule();
            schedule.setWoStepId(woStepId);
            schedule.setWorkorderId(step.getWorkorderId());
        }
        schedule.setWorkcenterId(workcenterId);
        schedule.setPlanStartTime(start);
        schedule.setPlanEndTime(end);
        schedule.setLockFlag(lockFlag == null ? 0 : lockFlag);
        if (schedule.getId() == null) {
            scheduleMapper.insert(schedule);
        } else {
            scheduleMapper.updateById(schedule);
        }
        // 同步回写工序行计划时间，过站/看板可直接读工序行
        step.setPlanStartTime(start);
        step.setPlanEndTime(end);
        woStepMapper.updateById(step);
    }

    @Override
    public List<ScheduleVO> listByWorkcenter(Long workcenterId, LocalDateTime from, LocalDateTime to) {
        var wrapper = Wrappers.lambdaQuery(PlanSchedule.class)
                .eq(PlanSchedule::getWorkcenterId, workcenterId)
                .ge(from != null, PlanSchedule::getPlanStartTime, from)
                .le(to != null, PlanSchedule::getPlanEndTime, to)
                .orderByAsc(PlanSchedule::getPlanStartTime);
        return scheduleMapper.selectList(wrapper).stream().map(ScheduleVO::from).toList();
    }
}
