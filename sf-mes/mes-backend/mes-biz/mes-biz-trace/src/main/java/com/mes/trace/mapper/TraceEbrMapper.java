package com.mes.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.trace.entity.TraceEbr;
import org.apache.ibatis.annotations.Mapper;

/**
 * TraceEbr Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface TraceEbrMapper extends BaseMapper<TraceEbr> {
}
