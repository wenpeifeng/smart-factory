package com.mes.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.basedata.entity.BaseOperation;
import org.apache.ibatis.annotations.Mapper;

/**
 * BaseOperation Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface BaseOperationMapper extends BaseMapper<BaseOperation> {
}
