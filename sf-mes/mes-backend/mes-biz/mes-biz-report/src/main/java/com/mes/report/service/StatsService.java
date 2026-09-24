package com.mes.report.service;

import com.mes.report.dto.vo.CockpitVO;
import com.mes.report.dto.vo.ScreenVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报表/驾驶舱/大屏统计服务（全部只读聚合）。
 */
public interface StatsService {

    /**
     * 车间电子看板大屏数据。
     *
     * @return 大屏 VO
     */
    ScreenVO screen();

    /**
     * 管理驾驶舱数据。
     *
     * @return 驾驶舱 VO
     */
    CockpitVO cockpit();

    /**
     * 工单达成报表。
     *
     * @param from 计划开工起
     * @param to   计划开工止
     * @return 行列表
     */
    List<Map<String, Object>> workorderAchievement(LocalDate from, LocalDate to);

    /**
     * 报工明细报表（近 N 天）。
     *
     * @param days 天数
     * @return 行列表
     */
    List<Map<String, Object>> stationDetail(int days);

    /**
     * 质量统计报表（按物料）。
     *
     * @return 行列表
     */
    List<Map<String, Object>> qualityByMaterial();
}
