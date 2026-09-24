package com.mes.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.equipment.entity.EqpMaintain;
import org.apache.ibatis.annotations.Mapper;

/**
 * EqpMaintain Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface EqpMaintainMapper extends BaseMapper<EqpMaintain> {
}
