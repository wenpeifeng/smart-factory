package com.erp.asset.dto.vo;

import java.math.BigDecimal;

/**
 * 类别维度统计原始行（MyBatis 按构造器<b>顺序</b>自动映射，列序与构参序一致；
 * 仅作 Mapper resultType，业务层不直接出参）。
 *
 * @param categoryId  类别 ID
 * @param originalSum 原值合计
 * @param depSum      截至期间累计折旧合计
 * @param netSum      净值合计
 */
public record CategoryStatsRawVO(Long categoryId, BigDecimal originalSum, BigDecimal depSum, BigDecimal netSum) {
}
