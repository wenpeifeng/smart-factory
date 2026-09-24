package com.erp.integration.controller;

import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.integration.config.IntegrationProperties;
import com.erp.integration.dto.MesOrderStatusDTO;
import com.erp.integration.dto.MesPickingDTO;
import com.erp.integration.dto.MesQualityResultDTO;
import com.erp.integration.dto.MesReturnDTO;
import com.erp.integration.dto.MesWorkReportDTO;
import com.erp.integration.service.MesInboundService;
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
 * MES 入站接口（MES -> ERP 回传；请求头 X-Integration-Token 鉴权）。
 * <p>五类：完工报工 / 领料 / 退料 / 质检结果 / 工单进度镜像。落地均为幂等
 * （int_message_log 唯一键兜底），重复回传直接返回成功。
 * 无登录上下文：本路径由 IntegrationSecurityConfig 独立放行，令牌校验在此承担
 * （stub 阶段静态配置 token，对称 MES 侧 /integration/erp/** 做法）。
 */
@Tag(name = "集成-MES入站")
@RestController
@RequestMapping("/integration/mes")
@RequiredArgsConstructor
public class MesInboundController {

    private final MesInboundService mesInboundService;
    private final IntegrationProperties properties;

    /**
     * 完工报工回传（woNo+operationName 幂等；产成品入库 + 工单累计回写）。
     */
    @Operation(summary = "完工报工回传")
    @PostMapping("/work-reports")
    public Result<String> receiveWorkReport(@RequestHeader("X-Integration-Token") String token,
                                            @Valid @RequestBody MesWorkReportDTO dto) {
        checkToken(token);
        return Result.ok(mesInboundService.receiveWorkReport(dto));
    }

    /**
     * 领料回传（pickNo 幂等；材料出库 PROD_USE）。
     */
    @Operation(summary = "领料回传")
    @PostMapping("/pickings")
    public Result<String> receivePicking(@RequestHeader("X-Integration-Token") String token,
                                         @Valid @RequestBody MesPickingDTO dto) {
        checkToken(token);
        return Result.ok(mesInboundService.receivePicking(dto));
    }

    /**
     * 退料回传（returnNo 幂等；反向入库冲销）。
     */
    @Operation(summary = "退料回传")
    @PostMapping("/returns")
    public Result<String> receiveReturn(@RequestHeader("X-Integration-Token") String token,
                                        @Valid @RequestBody MesReturnDTO dto) {
        checkToken(token);
        return Result.ok(mesInboundService.receiveReturn(dto));
    }

    /**
     * 质检结果回传（inspectNo 幂等；按结论分支处理）。
     */
    @Operation(summary = "质检结果回传")
    @PostMapping("/quality-results")
    public Result<String> receiveQualityResult(@RequestHeader("X-Integration-Token") String token,
                                               @Valid @RequestBody MesQualityResultDTO dto) {
        checkToken(token);
        return Result.ok(mesInboundService.receiveQualityResult(dto));
    }

    /**
     * 工单进度镜像（更新 mrp_work_order.mes_status，不改 ERP 状态机）。
     */
    @Operation(summary = "工单进度镜像")
    @PostMapping("/order-status")
    public Result<String> receiveOrderStatus(@RequestHeader("X-Integration-Token") String token,
                                             @Valid @RequestBody MesOrderStatusDTO dto) {
        checkToken(token);
        return Result.ok(mesInboundService.receiveOrderStatus(dto));
    }

    /** 入站令牌校验（stub 阶段静态配置，与 MES 出站令牌一致） */
    private void checkToken(String token) {
        String expected = properties.getMes().getInboundToken();
        if (!expected.equals(token)) {
            throw BusinessException.ofKey("erp.integration.token-invalid");
        }
    }
}
