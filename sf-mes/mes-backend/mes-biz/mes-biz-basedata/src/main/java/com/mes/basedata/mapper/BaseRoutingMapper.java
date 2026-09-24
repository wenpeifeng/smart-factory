package com.mes.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.basedata.entity.BaseRouting;
import org.apache.ibatis.annotations.Mapper;

/**
 * BaseRouting Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface BaseRoutingMapper extends BaseMapper<BaseRouting> {
}
