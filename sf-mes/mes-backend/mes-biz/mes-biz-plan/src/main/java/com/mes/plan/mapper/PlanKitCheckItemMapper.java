package com.mes.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.plan.entity.PlanKitCheckItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * PlanKitCheckItem Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface PlanKitCheckItemMapper extends BaseMapper<PlanKitCheckItem> {
}
