package com.mes.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报表/驾驶舱聚合 Mapper。
 * <p>跨模块只读聚合（plan/execution/quality/material/kanban 各表），
 * 报表口径集中在此维护；写操作仍归各业务模块。
 */
@Mapper
public interface StatsMapper {

    /** 工单状态分布：status -> cnt */
    @Select("SELECT status, COUNT(*) cnt FROM plan_workorder WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> woStatusDist();

    /** 近 N 天过站报工数量：date -> good/scrap */
    @Select("""
            SELECT DATE(station_time) d, SUM(good_qty) goodQty, SUM(scrap_qty) scrapQty
            FROM exe_station_record WHERE deleted = 0 AND station_time >= #{since}
            GROUP BY DATE(station_time) ORDER BY d
            """)
    List<Map<String, Object>> dailyOutput(@Param("since") java.time.LocalDateTime since);

    /** 检验汇总：类型 -> 抽检/合格/不合格 */
    @Select("""
            SELECT inspect_type inspectType, SUM(sample_qty) sampleQty,
                   SUM(qualified_qty) qualifiedQty, SUM(unqualified_qty) unqualifiedQty
            FROM qc_inspect WHERE deleted = 0 AND status = 1
            GROUP BY inspect_type
            """)
    List<Map<String, Object>> qualitySummary();

    /** 未关闭安灯 */
    @Select("SELECT type, COUNT(*) cnt FROM kb_andon WHERE deleted = 0 AND status <> 'CLOSED' GROUP BY type")
    List<Map<String, Object>> openAndons();

    /** 缺料工单数（最近齐套结果非 FULL 的工单） */
    @Select("""
            SELECT COUNT(DISTINCT k.workorder_id) cnt FROM plan_kit_check k
            WHERE k.deleted = 0 AND k.result <> 'FULL'
              AND k.id IN (SELECT MAX(id) FROM plan_kit_check WHERE deleted = 0 GROUP BY workorder_id)
            """)
    Integer shortWorkorders();

    /** 回传积压：FAILED 待重试数 */
    @Select("SELECT COUNT(*) cnt FROM int_push_log WHERE deleted = 0 AND status IN ('PENDING','FAILED')")
    Integer pendingPushes();

    /** 工单达成（报表）：按日期开完工汇总 */
    @Select("""
            SELECT w.wo_no woNo, m.name materialName, w.qty planQty, w.finish_qty finishQty,
                   w.scrap_qty scrapQty, w.status, w.plan_start_date planDate
            FROM plan_workorder w LEFT JOIN base_material m ON m.id = w.material_id
            WHERE w.deleted = 0 AND (#{from} IS NULL OR w.plan_start_date >= #{from})
              AND (#{to} IS NULL OR w.plan_start_date <= #{to})
            ORDER BY w.id DESC LIMIT 10000
            """)
    List<Map<String, Object>> workorderAchievement(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 报工明细（报表）：最近 N 天过站记录 */
    @Select("""
            SELECT r.record_no recordNo, r.wo_no woNo, r.operation_name operationName,
                   r.good_qty goodQty, r.scrap_qty scrapQty, r.rework_qty reworkQty,
                   r.worker_name workerName, r.station_time stationTime, r.result
            FROM exe_station_record r
            WHERE r.deleted = 0 AND r.station_time >= #{since}
            ORDER BY r.id DESC LIMIT 10000
            """)
    List<Map<String, Object>> stationDetail(@Param("since") java.time.LocalDateTime since);

    /** 质量统计（报表）：按物料汇总抽检/合格/不合格/合格率 */
    @Select("""
            SELECT m.code materialCode, m.name materialName,
                   SUM(i.sample_qty) sampleQty, SUM(i.qualified_qty) qualifiedQty,
                   SUM(i.unqualified_qty) unqualifiedQty
            FROM qc_inspect i LEFT JOIN base_material m ON m.id = i.material_id
            WHERE i.deleted = 0 AND i.status = 1
            GROUP BY i.material_id, m.code, m.name ORDER BY sampleQty DESC LIMIT 10000
            """)
    List<Map<String, Object>> qualityByMaterial();

    /** 大屏：当前 WIP 总量与工序在制 Top */
    @Select("""
            SELECT w.operation_name operationName, SUM(w.wip_qty) wipQty
            FROM exe_wip w WHERE w.deleted = 0
            GROUP BY w.operation_name ORDER BY wipQty DESC LIMIT 10
            """)
    List<Map<String, Object>> wipByOperation();

    /** 大屏：工单进度榜（未完工按完工率降序） */
    @Select("""
            SELECT w.wo_no woNo, m.name materialName, w.qty planQty, w.finish_qty finishQty,
                   ROUND(w.finish_qty / w.qty * 100, 1) progressPct, w.status
            FROM plan_workorder w LEFT JOIN base_material m ON m.id = w.material_id
            WHERE w.deleted = 0 AND w.status IN ('RELEASED','PRODUCING')
            ORDER BY progressPct DESC LIMIT 10
            """)
    List<Map<String, Object>> woProgress();
}
