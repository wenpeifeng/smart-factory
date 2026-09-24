package com.mes.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.plan.entity.PlanWoStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * PlanWoStep Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface PlanWoStepMapper extends BaseMapper<PlanWoStep> {
}
