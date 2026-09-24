package com.mes.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.basedata.entity.BaseBomItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * BaseBomItem Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface BaseBomItemMapper extends BaseMapper<BaseBomItem> {
}
