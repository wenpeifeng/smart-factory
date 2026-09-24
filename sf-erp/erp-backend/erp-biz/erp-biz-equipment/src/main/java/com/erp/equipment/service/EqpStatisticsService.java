package com.erp.equipment.service;

import com.erp.equipment.dto.vo.StatisticsVO;

/**
 * 设备管理统计 Service（驾驶舱/报表口径）。
 */
public interface EqpStatisticsService {

    /**
     * 汇总统计：设备状态分布 + 当月维修次数 TOP5 设备 + 当月备件消耗成本。
     *
     * @return 统计结果（四个状态全量返回，无设备的补 0）
     */
    StatisticsVO statistics();
}
