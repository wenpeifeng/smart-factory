package com.mes.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.quality.entity.QcInspect;
import org.apache.ibatis.annotations.Mapper;

/**
 * QcInspect Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface QcInspectMapper extends BaseMapper<QcInspect> {
}
