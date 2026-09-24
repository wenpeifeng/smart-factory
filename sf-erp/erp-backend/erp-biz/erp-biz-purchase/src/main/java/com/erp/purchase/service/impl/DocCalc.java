package com.erp.purchase.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 单据金额计算工具 —— 全链 BigDecimal，四舍五入统一 HALF_UP 保留 2 位。
 * 金额规则：明细含税金额 = 数量 × 含税单价；主表价税合计 = 明细金额之和。
 */
final class DocCalc {

    private DocCalc() {
    }

    /**
     * 明细含税金额 = qty × price，HALF_UP 保留 2 位。
     */
    static BigDecimal lineAmount(BigDecimal qty, BigDecimal price) {
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 主表价税合计 = 明细金额之和（再 HALF_UP 2 位收口，消除累计浮差）。
     */
    static BigDecimal total(List<BigDecimal> lineAmounts) {
        return lineAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
