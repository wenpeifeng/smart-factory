package com.erp.basedata.service;

import com.erp.basedata.dto.vo.PricingQuoteVO;

import java.math.BigDecimal;

/**
 * 定价引擎（销售报价/订单录单参照的唯一取价入口）。
 * <p>
 * 优先级：特价审批（通过且在生效期）&gt; 等级价（客户等级×物料）&gt;
 * 批量价（满足 qty 的最低价格档）&gt; 物料参考成本×加成系数（sal.price.markup，默认 1.3）。
 */
public interface PricingService {

    /**
     * 报价：按客户+物料+数量计算建议单价。
     *
     * @param customerId 客户 ID（等级参与等级价匹配）
     * @param materialId 物料 ID（refCost 参与加成兜底）
     * @param qty        数量（参与批量价阶梯匹配，可空按 0 处理）
     * @return 报价结果（含命中策略与参照价列表；无可用价时 hitStrategy=NONE、unitPrice=null）
     * @throws com.erp.common.exception.BusinessException DATA_NOT_EXISTS 客户/物料不存在
     */
    PricingQuoteVO quote(Long customerId, Long materialId, BigDecimal qty);
}
