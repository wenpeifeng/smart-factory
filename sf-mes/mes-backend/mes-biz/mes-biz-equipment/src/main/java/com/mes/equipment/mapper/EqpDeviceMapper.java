package com.mes.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.equipment.entity.EqpDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * EqpDevice Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface EqpDeviceMapper extends BaseMapper<EqpDevice> {
}
