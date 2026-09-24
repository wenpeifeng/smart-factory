package com.mes.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.basedata.entity.BaseWorkcenter;
import org.apache.ibatis.annotations.Mapper;

/**
 * BaseWorkcenter Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface BaseWorkcenterMapper extends BaseMapper<BaseWorkcenter> {
}
