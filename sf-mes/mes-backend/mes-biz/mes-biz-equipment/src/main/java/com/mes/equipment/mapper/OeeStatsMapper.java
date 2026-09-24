package com.mes.equipment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * OEE 统计聚合 Mapper。
 * <p>跨表只读聚合（exe_station_record 归 execution、plan_wo_step 归 plan），
 * 避免多模块服务互调；统计口径变更时只改本文件。
 */
@Mapper
public interface OeeStatsMapper {

    /**
     * 按工作中心聚合过站工时与数量、理论工时（单位分钟/件数）。
     *
     * @param workcenterId 工作中心（空=全部）
     * @param from         开始时间
     * @param to           结束时间
     * @return workcenterId / run_minutes / good_qty / scrap_qty / theory_minutes
     */
    @Select("""
            SELECT r.workcenter_id        AS workcenterId,
                   COALESCE(SUM(r.work_minutes), 0) AS runMinutes,
                   COALESCE(SUM(r.good_qty), 0)     AS goodQty,
                   COALESCE(SUM(r.scrap_qty), 0)    AS scrapQty,
                   COALESCE(SUM(r.good_qty * s.unit_minutes), 0) AS theoryMinutes
            FROM exe_station_record r
            JOIN plan_wo_step s ON s.id = r.wo_step_id
            WHERE r.deleted = 0
              AND r.station_time >= #{from} AND r.station_time <= #{to}
              AND (#{workcenterId} IS NULL OR r.workcenter_id = #{workcenterId})
            GROUP BY r.workcenter_id
            """)
    List<Map<String, Object>> aggregate(@Param("workcenterId") Long workcenterId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);
}
