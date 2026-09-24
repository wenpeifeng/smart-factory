package com.mes.material.service.plan;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.material.entity.MatShopStock;
import com.mes.material.mapper.MatShopStockMapper;
import com.mes.plan.service.StockAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 齐套分析可用量适配器：material 模块实现 plan 模块声明的 SPI
 * （依赖方向 material → plan 合法），替换此前 plan 直查 mat_shop_stock 的临时实现。
 */
@Service
@RequiredArgsConstructor
public class ShopStockAvailabilityAdapter implements StockAvailabilityService {

    private final MatShopStockMapper shopStockMapper;

    @Override
    public BigDecimal getAvailableQty(Long materialId) {
        var wrapper = Wrappers.lambdaQuery(MatShopStock.class)
                .eq(MatShopStock::getMaterialId, materialId)
                .select(MatShopStock::getQty);
        return shopStockMapper.selectList(wrapper).stream()
                .map(MatShopStock::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
