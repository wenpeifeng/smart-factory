package com.erp.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.cost.entity.CostPeriod;
import org.apache.ibatis.annotations.Param;

/**
 * 存货核算期间 Mapper。
 */
public interface CostPeriodMapper extends BaseMapper<CostPeriod> {

    /** 按期间取行并加锁（FOR UPDATE），结账事务串行化用；无行返回 null */
    CostPeriod selectByPeriodForUpdate(@Param("period") String period);
}
