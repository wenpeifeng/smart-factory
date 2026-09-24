package com.mes.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.plan.entity.PlanWorkorder;
import org.apache.ibatis.annotations.Mapper;

/**
 * PlanWorkorder Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface PlanWorkorderMapper extends BaseMapper<PlanWorkorder> {
}
