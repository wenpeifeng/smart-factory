package com.erp.basedata.controller;

import com.erp.basedata.dto.vo.PricingQuoteVO;
import com.erp.basedata.service.PricingService;
import com.erp.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 定价引擎接口（销售订单/报价单录单参照）。
 * <p>
 * 优先级：特价审批 &gt; 等级价 &gt; 批量价 &gt; 参考成本×加成系数；
 * 登录即可调用（录单参照，与下拉 options 同口径）。
 */
@Tag(name = "基础资料-定价引擎")
@RestController
@RequestMapping("/basedata/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    /** 报价：客户+物料+数量 → 建议单价 + 命中策略 + 参照价列表 */
    @Operation(summary = "定价引擎报价(录单参照)")
    @GetMapping("/quote")
    public Result<PricingQuoteVO> quote(
            @RequestParam Long customerId,
            @RequestParam Long materialId,
            @RequestParam(required = false) BigDecimal qty) {
        return Result.ok(pricingService.quote(customerId, materialId, qty));
    }
}
