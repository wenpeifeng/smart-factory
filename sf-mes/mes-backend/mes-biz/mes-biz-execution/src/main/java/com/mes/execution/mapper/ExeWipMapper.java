package com.mes.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.execution.entity.ExeWip;
import org.apache.ibatis.annotations.Mapper;

/**
 * ExeWip Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface ExeWipMapper extends BaseMapper<ExeWip> {
}
