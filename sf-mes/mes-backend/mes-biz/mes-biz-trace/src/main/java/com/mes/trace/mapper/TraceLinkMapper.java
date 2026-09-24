package com.mes.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.trace.entity.TraceLink;
import org.apache.ibatis.annotations.Mapper;

/**
 * TraceLink Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface TraceLinkMapper extends BaseMapper<TraceLink> {
}
