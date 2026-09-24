package com.mes.plan.service;

import java.math.BigDecimal;

/**
 * 物料可用量查询服务（齐套分析数据源）。
 * <p>可用量 = 线边仓库存（mat_shop_stock）+ ERP 仓库可用量（stub 阶段恒为 0）。
 */
public interface StockAvailabilityService {

    /**
     * 查询物料在全部线边仓的可用量合计。
     *
     * @param materialId 物料 id
     * @return 可用量（无量纲合计）
     */
    BigDecimal getAvailableQty(Long materialId);
}
