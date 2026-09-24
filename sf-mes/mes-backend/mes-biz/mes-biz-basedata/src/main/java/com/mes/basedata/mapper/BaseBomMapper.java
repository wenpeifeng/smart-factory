package com.mes.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.basedata.entity.BaseBom;
import org.apache.ibatis.annotations.Mapper;

/**
 * BaseBom Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface BaseBomMapper extends BaseMapper<BaseBom> {
}
