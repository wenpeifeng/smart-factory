package com.erp.asset.dto.vo;

import java.math.BigDecimal;

/**
 * 统计单行 VO（部门或类别维度）。
 *
 * @param dimensionId           维度 ID（部门 ID 或类别 ID）
 * @param dimensionName         维度名称（部门名 / 类别名）
 * @param originalValue         原值合计
 * @param accumulatedDepreciation 截至期间累计折旧合计
 * @param netValue              净值合计
 */
public record StatisticsRowVO(
        Long dimensionId,
        String dimensionName,
        BigDecimal originalValue,
        BigDecimal accumulatedDepreciation,
        BigDecimal netValue) {
}
