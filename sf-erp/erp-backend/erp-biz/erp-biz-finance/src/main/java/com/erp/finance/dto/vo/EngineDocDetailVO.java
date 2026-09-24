package com.erp.finance.dto.vo;

import java.math.BigDecimal;

/**
 * 凭证引擎来源单据明细行（归一化视图，V521）。
 * <p>
 * 不同单据类型的有效列：
 * <ul>
 *   <li>采购入库/退货：amount=含税行金额、taxRate=税率%（不含税=amount÷(1+税率%)价税分离）；</li>
 *   <li>销售出库：amount=不含税行金额、actualCost=结转成本快照（V2101，历史行可能 NULL）；</li>
 *   <li>销售退货：qty + originQty/originActualCost（按退货数量分摊原出库行成本）。</li>
 * </ul>
 *
 * @param qty              行数量
 * @param amount           行金额（口径见上）
 * @param taxRate          税率%（无税率概念的单据为 0）
 * @param actualCost       销售出库行实际成本快照
 * @param originQty        原出库行数量（销售退货分摊用）
 * @param originActualCost 原出库行实际成本（销售退货分摊用）
 */
public record EngineDocDetailVO(BigDecimal qty, BigDecimal amount, BigDecimal taxRate,
                                BigDecimal actualCost, BigDecimal originQty,
                                BigDecimal originActualCost) {
}
