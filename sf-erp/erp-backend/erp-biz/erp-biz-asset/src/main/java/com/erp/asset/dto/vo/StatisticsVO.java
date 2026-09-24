package com.erp.asset.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 固定资产统计 VO（截至指定期间的部门/类别双维度汇总）。
 *
 * @param period      统计期间（yyyyMM）
 * @param byDept      部门维度行
 * @param byCategory  类别维度行
 * @param total       全部在册资产合计（原值/累计折旧/净值）
 */
public record StatisticsVO(
        String period,
        List<StatisticsRowVO> byDept,
        List<StatisticsRowVO> byCategory,
        Total total) {

    /** 合计行 */
    public record Total(BigDecimal originalValue, BigDecimal accumulatedDepreciation, BigDecimal netValue) {
    }
}
