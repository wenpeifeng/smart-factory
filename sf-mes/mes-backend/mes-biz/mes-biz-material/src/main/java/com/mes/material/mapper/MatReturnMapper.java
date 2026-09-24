package com.mes.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.material.entity.MatReturn;
import org.apache.ibatis.annotations.Mapper;

/**
 * MatReturn Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface MatReturnMapper extends BaseMapper<MatReturn> {
}
