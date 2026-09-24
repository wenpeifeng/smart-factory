package com.mes.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.basedata.entity.BaseRoutingStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * BaseRoutingStep Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface BaseRoutingStepMapper extends BaseMapper<BaseRoutingStep> {
}
