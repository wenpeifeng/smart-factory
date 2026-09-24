package com.mes.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.quality.entity.QcTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * QcTemplate Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface QcTemplateMapper extends BaseMapper<QcTemplate> {
}
