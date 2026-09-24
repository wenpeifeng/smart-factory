package com.mes.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.equipment.entity.EqpCheck;
import org.apache.ibatis.annotations.Mapper;

/**
 * EqpCheck Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface EqpCheckMapper extends BaseMapper<EqpCheck> {
}
