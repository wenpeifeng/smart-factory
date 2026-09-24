package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.BaseUnit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计量单位 Mapper。
 * <p>基础 CRUD 由 MyBatis-Plus 自动提供，复杂查询走 XML（resources/mapper/basedata/）。
 */
@Mapper
public interface BaseUnitMapper extends BaseMapper<BaseUnit> {
}
