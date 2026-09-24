package com.mes.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.quality.entity.QcSpcRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * QcSpcRule Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface QcSpcRuleMapper extends BaseMapper<QcSpcRule> {
}
