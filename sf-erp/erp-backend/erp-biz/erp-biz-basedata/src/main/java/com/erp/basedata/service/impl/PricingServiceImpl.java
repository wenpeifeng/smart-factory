package com.erp.basedata.service.impl;

import com.erp.basedata.dto.vo.PricingQuoteVO;
import com.erp.basedata.entity.BaseCustomer;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.SalPriceLevel;
import com.erp.basedata.entity.SalPriceQty;
import com.erp.basedata.entity.SalPriceSpecial;
import com.erp.basedata.service.BaseCustomerService;
import com.erp.basedata.service.BaseMaterialService;
import com.erp.basedata.service.PricingService;
import com.erp.basedata.service.SalPriceSpecialService;
import com.erp.basedata.service.SalPriceStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 定价引擎实现。
 * <p>
 * 优先级（高→低）：特价审批（通过且在生效期）&gt; 等级价（客户等级×物料）&gt;
 * 批量价（满足 qty 的最低价格档）&gt; 物料参考成本×加成系数。
 * 返回单价统一 HALF_UP 2 位（与销售单据单价精度一致）；
 * 参照价列表（references）保留各策略原始命中价，供前端并列展示。
 * <pre>{@code
 *   特价审批(通过+生效期内) ──命中──> 返回
 *        │未命中
 *   等级价(物料+客户等级) ──命中──> 返回
 *        │未命中
 *   批量价(满足qty的最低档) ──命中──> 返回
 *        │未命中
 *   ref_cost × sal.price.markup(默认1.3) ──> 返回（ref_cost 空则 NONE）
 * }</pre>
 */
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final BaseCustomerService customerService;
    private final BaseMaterialService materialService;
    private final SalPriceSpecialService specialService;
    private final SalPriceStrategyService strategyService;

    /** 加成系数：sal.price.markup，默认 1.3（ref_cost × markup 作为无策略时的兜底售价） */
    @Value("${sal.price.markup:1.3}")
    private BigDecimal markup;

    @Override
    public PricingQuoteVO quote(Long customerId, Long materialId, BigDecimal qty) {
        BaseCustomer customer = customerService.getEntityById(customerId);
        BaseMaterial material = materialService.getEntityById(materialId);
        String level = normalizeLevel(customer.getLevel());
        BigDecimal safeQty = qty == null ? BigDecimal.ZERO : qty;

        PricingQuoteVO vo = new PricingQuoteVO();
        vo.setCustomerId(customerId);
        vo.setCustomerLevel(level);
        vo.setMaterialId(materialId);
        vo.setMaterialCode(material.getCode());
        vo.setMaterialName(material.getName());
        vo.setQty(safeQty);

        // 1) 特价审批（最高优先级；findEffective 内部会惰性同步 workflow 审批结果）
        SalPriceSpecial special = specialService.findEffective(customerId, materialId);
        if (special != null) {
            vo.getReferences().add(new PricingQuoteVO.RefItem(PricingQuoteVO.HIT_SPECIAL,
                    "特价(≤" + (special.getValidTo() == null ? "长期" : special.getValidTo()) + ")",
                    special.getSpecialPrice(), true));
        }
        // 2) 等级价
        SalPriceLevel levelPrice = strategyService.findEnabledLevel(materialId, level);
        if (levelPrice != null) {
            vo.getReferences().add(new PricingQuoteVO.RefItem(PricingQuoteVO.HIT_LEVEL,
                    "等级价(" + level + "级)", levelPrice.getPrice(), false));
        }
        // 3) 批量价（满足 qty 的最低价格档）
        SalPriceQty qtyPrice = strategyService.matchQtyTier(materialId, safeQty);
        if (qtyPrice != null) {
            vo.getReferences().add(new PricingQuoteVO.RefItem(PricingQuoteVO.HIT_QTY,
                    "批量价(≥" + qtyPrice.getMinQty().stripTrailingZeros().toPlainString() + ")",
                    qtyPrice.getPrice(), false));
        }
        // 4) 参考成本加成（兜底）
        BigDecimal refCost = material.getRefCost();
        BigDecimal markupPrice = null;
        if (refCost != null && refCost.signum() > 0 && markup != null && markup.signum() > 0) {
            markupPrice = refCost.multiply(markup).setScale(2, RoundingMode.HALF_UP);
        }

        // 按优先级取第一个可用价
        if (special != null) {
            applyHit(vo, PricingQuoteVO.HIT_SPECIAL, special.getSpecialPrice(),
                    "特价(" + (special.getValidTo() == null ? "长期有效" : "至 " + special.getValidTo()) + ")");
        } else if (levelPrice != null) {
            applyHit(vo, PricingQuoteVO.HIT_LEVEL, levelPrice.getPrice(), "客户等级 " + level + " 等级价");
        } else if (qtyPrice != null) {
            applyHit(vo, PricingQuoteVO.HIT_QTY, qtyPrice.getPrice(),
                    "批量价(≥" + qtyPrice.getMinQty().stripTrailingZeros().toPlainString() + ")");
        } else if (markupPrice != null) {
            vo.getReferences().add(new PricingQuoteVO.RefItem(PricingQuoteVO.HIT_MARKUP,
                    "成本加成(参考成本" + refCost.stripTrailingZeros().toPlainString()
                            + "×" + markup.stripTrailingZeros().toPlainString() + ")",
                    markupPrice, true));
            applyHit(vo, PricingQuoteVO.HIT_MARKUP, markupPrice,
                    "参考成本 " + refCost.stripTrailingZeros().toPlainString()
                            + " × " + markup.stripTrailingZeros().toPlainString());
        } else {
            vo.setHitStrategy(PricingQuoteVO.HIT_NONE);
            vo.setHitStrategyLabel(PricingQuoteVO.labelOf(PricingQuoteVO.HIT_NONE));
            vo.setHitReason("未维护策略价且物料参考成本为空，无法自动定价");
        }
        return vo;
    }

    /** 应用命中策略：标 hit、单价统一 2 位（与销售单据精度一致） */
    private void applyHit(PricingQuoteVO vo, String strategy, BigDecimal price, String reason) {
        vo.setHitStrategy(strategy);
        vo.setHitStrategyLabel(PricingQuoteVO.labelOf(strategy));
        vo.setHitReason(reason);
        vo.setUnitPrice(price.setScale(2, RoundingMode.HALF_UP));
        // references 中对应条目标记命中
        vo.getReferences().stream()
                .filter(ref -> strategy.equals(ref.getStrategy()))
                .forEach(ref -> ref.setHit(true));
    }

    /** 客户等级归一化：空/历史数据默认 B */
    private String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "B";
        }
        return level.trim().toUpperCase();
    }
}
