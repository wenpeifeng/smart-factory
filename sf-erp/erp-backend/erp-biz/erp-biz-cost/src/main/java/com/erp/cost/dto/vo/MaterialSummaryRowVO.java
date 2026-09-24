package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 物料收发存汇总行 VO（期末 = 期初 + 入 − 出，由服务层计算）。
 */
public record MaterialSummaryRowVO(
        Long materialId,
        String materialCode,
        String materialName,
        BigDecimal beginQty,
        BigDecimal beginAmount,
        BigDecimal inQty,
        BigDecimal inAmount,
        BigDecimal outQty,
        BigDecimal outAmount,
        BigDecimal endQty,
        BigDecimal endAmount) {
}
