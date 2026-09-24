package com.mes.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.quality.entity.QcInspectItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * QcInspectItem Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface QcInspectItemMapper extends BaseMapper<QcInspectItem> {
}
