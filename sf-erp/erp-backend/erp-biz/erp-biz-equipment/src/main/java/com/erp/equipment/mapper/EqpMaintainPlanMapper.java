package com.erp.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.equipment.dto.request.PlanQuery;
import com.erp.equipment.dto.vo.PlanVO;
import com.erp.equipment.entity.EqpMaintainPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 保养计划 Mapper。分页/到期清单走 XML（JOIN 设备台账带出编码名称）。
 */
@Mapper
public interface EqpMaintainPlanMapper extends BaseMapper<EqpMaintainPlan> {

    /**
     * 保养计划分页（JOIN eqp_device 带出设备编码/名称/车间）。
     *
     * @param page  MP 分页对象
     * @param query 查询条件（deviceId 精确 / dueOnly 只看到期与已逾期）
     * @return 计划分页结果
     */
    IPage<PlanVO> selectPlanPage(IPage<PlanVO> page, @Param("q") PlanQuery query);

    /**
     * 到期清单：next_date &lt;= 今天 + days 天 的计划（含已逾期），按到期日升序。
     *
     * @param days 提前预警天数（7 = 已到期 + 7 天内到期）
     * @return 到期计划列表
     */
    List<PlanVO> selectDuePlans(@Param("days") int days);
}
