package com.mes.plan.service;

import com.mes.plan.dto.vo.ScheduleVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工序排程服务（工作中心甘特时间片；只做手动排，不做自动 APS）。
 */
public interface ScheduleService {

    /**
     * 保存/更新某工序的排程时间片（存在即覆盖时间与工作中心）。
     *
     * @param woStepId    工单工序 id
     * @param workcenterId 工作中心 id
     * @param start       计划开始时间
     * @param end         计划结束时间
     * @param lockFlag    锁定标志（1 锁定）
     */
    void save(Long woStepId, Long workcenterId, LocalDateTime start, LocalDateTime end, Integer lockFlag);

    /**
     * 查询工作中心在时间范围内的排程时间片（甘特数据）。
     *
     * @param workcenterId 工作中心 id
     * @param from         开始时间（可空）
     * @param to           结束时间（可空）
     * @return 时间片列表
     */
    List<ScheduleVO> listByWorkcenter(Long workcenterId, LocalDateTime from, LocalDateTime to);
}
