package com.mes.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.plan.entity.PlanSchedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * PlanSchedule Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface PlanScheduleMapper extends BaseMapper<PlanSchedule> {
}
