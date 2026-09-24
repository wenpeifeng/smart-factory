package com.erp.mrp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.mrp.dto.vo.WorkCenterLoadVO;
import com.erp.mrp.entity.MrpWoStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单工序任务 Mapper —— 单表 CRUD 由 MyBatis-Plus 提供；
 * 另含能力计划(CRP)负荷聚合查询。
 */
@Mapper
public interface MrpWoStepMapper extends BaseMapper<MrpWoStep> {

    /**
     * 按工作中心汇总未完工工序任务的负荷工时（分钟）：
     * 负荷 = SUM(计划数量 × 单件工时)，status != FINISHED 视为未完工。
     * <p>
     * 注意：自定义 SQL 不走 MP 逻辑删除拦截，需显式 deleted = 0。
     *
     * @return workCenterId → 负荷分钟数（工作中心为空的任务不参与分组）
     */
    @Select("""
            SELECT work_center_id AS workCenterId,
                   COALESCE(SUM(plan_qty * unit_minutes), 0) AS loadMinutes
            FROM mrp_wo_step
            WHERE deleted = 0 AND status <> 'FINISHED' AND work_center_id IS NOT NULL
            GROUP BY work_center_id
            """)
    List<WorkCenterLoadVO> sumLoadMinutesByWorkCenter();

    /**
     * 统计某生产订单已生成的工序任务数（下达幂等防重）。
     *
     * @param workOrderId 生产订单ID
     * @return 任务数
     */
    @Select("SELECT COUNT(*) FROM mrp_wo_step WHERE deleted = 0 AND work_order_id = #{workOrderId}")
    long countByWorkOrderId(@Param("workOrderId") Long workOrderId);
}
