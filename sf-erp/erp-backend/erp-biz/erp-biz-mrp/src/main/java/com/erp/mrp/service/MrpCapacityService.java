package com.erp.mrp.service;

import com.erp.mrp.dto.vo.CapacityLoadResultVO;

import java.time.LocalDate;

/**
 * 能力计划(CRP 简版)服务 —— 各工作中心期间负荷 vs 可用工时。
 */
public interface MrpCapacityService {

    /**
     * 能力负荷查询：
     * 负荷工时 = 未完工工序任务的 计划数量×单件工时 汇总（简版不按期间切分）；
     * 可用工时 = 每日产能工时 × 期间工作日数（周六周日不计）；
     * 负荷率 &gt; 100% 进入超载列表。
     *
     * @param from         期间起（含），空取今天
     * @param to           期间止（含），空取 from+6 天
     * @param workCenterId 工作中心过滤，可空
     * @return 期间参数 + 各工作中心负荷行 + 超载列表
     * @throws com.erp.common.exception.BusinessException 结束日期早于开始日期
     */
    CapacityLoadResultVO load(LocalDate from, LocalDate to, Long workCenterId);
}
