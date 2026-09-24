package com.mes.integration.controller;

import com.mes.common.exception.BusinessException;
import com.mes.common.result.Result;
import com.mes.integration.config.IntegrationProperties;
import com.mes.integration.erp.dto.ErpBomDTO;
import com.mes.integration.erp.dto.ErpMaterialDTO;
import com.mes.integration.erp.dto.ErpProductionOrderDTO;
import com.mes.integration.erp.dto.ErpRoutingDTO;
import com.mes.integration.service.ErpInboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 入站接口（ERP -> MES 下推；请求头 X-Integration-Token 鉴权）。
 * <p>三类：生产订单 / 物料主数据 / BOM 与工艺路线。落地均为幂等。
 * stub 阶段 token 固定配置值，ERP 侧就绪后换 OAuth2 client_credentials。
 */
@Tag(name = "集成-ERP入站")
@RestController
@RequestMapping("/integration/erp")
@RequiredArgsConstructor
public class ErpInboundController {

    private final ErpInboundService erpInboundService;
    private final IntegrationProperties properties;

    /**
     * 生产订单下推（幂等建工单）。
     */
    @Operation(summary = "生产订单下推")
    @PostMapping("/orders")
    public Result<Long> receiveOrder(@RequestHeader("X-Integration-Token") String token,
                                     @Valid @RequestBody ErpProductionOrderDTO dto) {
        checkToken(token);
        return Result.ok(erpInboundService.receiveProductionOrder(dto));
    }

    /**
     * 物料主数据下推（幂等 upsert）。
     */
    @Operation(summary = "物料主数据下推")
    @PostMapping("/materials")
    public Result<Long> receiveMaterial(@RequestHeader("X-Integration-Token") String token,
                                        @Valid @RequestBody ErpMaterialDTO dto) {
        checkToken(token);
        return Result.ok(erpInboundService.receiveMaterial(dto));
    }

    /**
     * BOM 下推（幂等 upsert）。
     */
    @Operation(summary = "BOM下推")
    @PostMapping("/boms")
    public Result<Long> receiveBom(@RequestHeader("X-Integration-Token") String token,
                                   @Valid @RequestBody ErpBomDTO dto) {
        checkToken(token);
        return Result.ok(erpInboundService.receiveBom(dto));
    }

    /**
     * 工艺路线下推（幂等 upsert）。
     */
    @Operation(summary = "工艺路线下推")
    @PostMapping("/routings")
    public Result<Long> receiveRouting(@RequestHeader("X-Integration-Token") String token,
                                       @Valid @RequestBody ErpRoutingDTO dto) {
        checkToken(token);
        return Result.ok(erpInboundService.receiveRouting(dto));
    }

    /** 入站令牌校验（stub 阶段静态配置） */
    private void checkToken(String token) {
        String expected = properties.getErp().getInboundToken();
        if (!expected.equals(token)) {
            throw new BusinessException("ERP 入站令牌无效");
        }
    }
}
