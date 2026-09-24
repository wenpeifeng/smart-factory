package com.mes.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.basedata.entity.BaseMaterial;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物料 Mapper。
 * <p>分页/编码/名称/分类筛选均为简单条件，Lambda Wrapper 即可，无需 XML。
 */
@Mapper
public interface BaseMaterialMapper extends BaseMapper<BaseMaterial> {
}
