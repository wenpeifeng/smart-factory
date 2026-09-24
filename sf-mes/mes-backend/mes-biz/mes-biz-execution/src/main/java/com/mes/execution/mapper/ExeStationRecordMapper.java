package com.mes.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.execution.entity.ExeStationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * ExeStationRecord Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface ExeStationRecordMapper extends BaseMapper<ExeStationRecord> {
}
