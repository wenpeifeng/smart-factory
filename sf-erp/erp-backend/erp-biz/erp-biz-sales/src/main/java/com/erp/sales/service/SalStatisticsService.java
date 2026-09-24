package com.erp.sales.service;

import com.erp.sales.dto.vo.SalDailyStatVO;
import com.erp.sales.dto.vo.SalRankVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售统计 Service。
 * <p>
 * 口径：仅统计"已审核"销售出库单；成本按 base_material.ref_cost × 数量；
 * 退货不冲减销售额（如需净额口径由财务报表负责）。
 */
public interface SalStatisticsService {

    /**
     * 按日汇总销售额/成本/毛利。
     *
     * @param from 开始日期（含，必填）
     * @param to   结束日期（含，必填，不早于 from）
     * @return 统计行列表（按日期升序，无单据的日期不出现）
     * @throws com.erp.common.exception.BusinessException 日期参数缺失或区间非法
     */
    List<SalDailyStatVO> dailyStats(LocalDate from, LocalDate to);

    /**
     * 销售排行 TOP10。
     *
     * @param type 维度：material=物料 / customer=客户
     * @param from 开始日期（含，可空=不限）
     * @param to   结束日期（含，可空=不限）
     * @return 排行行列表（按销售额降序，最多 10 行）
     * @throws com.erp.common.exception.BusinessException type 取值非法
     */
    List<SalRankVO> rank(String type, LocalDate from, LocalDate to);
}
