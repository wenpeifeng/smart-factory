package com.erp.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.budget.entity.BudgetControlConfig;
import org.apache.ibatis.annotations.Param;

/**
 * 预算控制配置 Mapper（每方案一条，service 层 selectOne+insert/update 维护）。
 */
public interface BudgetControlConfigMapper extends BaseMapper<BudgetControlConfig> {

    /**
     * 按方案查在用配置（只取一条）。
     *
     * @return 无配置返回 null
     */
    BudgetControlConfig selectByPlanId(@Param("planId") Long planId);
}
