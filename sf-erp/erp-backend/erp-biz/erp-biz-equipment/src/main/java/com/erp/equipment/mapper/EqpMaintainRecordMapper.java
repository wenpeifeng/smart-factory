package com.erp.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.equipment.entity.EqpMaintainRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 保养执行记录 Mapper（无自定义 SQL，纯 MP 能力）。
 */
@Mapper
public interface EqpMaintainRecordMapper extends BaseMapper<EqpMaintainRecord> {
}
