package com.mes.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.material.entity.MatPickingItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * MatPickingItem Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface MatPickingItemMapper extends BaseMapper<MatPickingItem> {
}
