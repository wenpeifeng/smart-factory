package com.mes.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.quality.entity.QcReject;
import org.apache.ibatis.annotations.Mapper;

/**
 * QcReject Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface QcRejectMapper extends BaseMapper<QcReject> {
}
