package com.erp.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.budget.dto.query.BudgetPlanQuery;
import com.erp.budget.dto.vo.BudgetPlanVO;
import com.erp.budget.entity.BudgetPlan;
import org.apache.ibatis.annotations.Param;

/**
 * 预算方案 Mapper。
 */
public interface BudgetPlanMapper extends BaseMapper<BudgetPlan> {

    /**
     * 分页查询方案（只读关联 sys_user 编制人昵称、budget_control_config 控制配置）。
     */
    IPage<BudgetPlanVO> selectPlanPage(Page<BudgetPlanVO> page, @Param("q") BudgetPlanQuery q);
}
