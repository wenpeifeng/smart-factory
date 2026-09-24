package com.mes.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.material.entity.MatPicking;
import org.apache.ibatis.annotations.Mapper;

/**
 * MatPicking Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface MatPickingMapper extends BaseMapper<MatPicking> {
}
