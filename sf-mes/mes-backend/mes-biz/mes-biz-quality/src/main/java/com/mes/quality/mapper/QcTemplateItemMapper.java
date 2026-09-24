package com.mes.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.quality.entity.QcTemplateItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * QcTemplateItem Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface QcTemplateItemMapper extends BaseMapper<QcTemplateItem> {
}
