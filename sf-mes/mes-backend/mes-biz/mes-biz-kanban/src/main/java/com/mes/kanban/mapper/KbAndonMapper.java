package com.mes.kanban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.kanban.entity.KbAndon;
import org.apache.ibatis.annotations.Mapper;

/**
 * KbAndon Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface KbAndonMapper extends BaseMapper<KbAndon> {
}
