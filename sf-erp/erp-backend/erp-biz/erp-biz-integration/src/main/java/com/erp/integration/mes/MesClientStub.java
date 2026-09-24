package com.erp.integration.mes;

import com.erp.integration.dto.MesBomDTO;
import com.erp.integration.dto.MesMaterialDTO;
import com.erp.integration.dto.MesProductionOrderDTO;
import com.erp.integration.dto.MesRoutingDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MES 出站客户端契约桩。
 * <p>MES 侧开放接口未就绪前的默认实现：仅记录日志并视为推送成功，
 * 使下推链路（outbox / 重试 / 对账）可先行联调（与 MES 侧 ErpClientStub 对称）。
 * MES 就绪后置 {@code erp.integration.mes.mode=http} 切换真实客户端。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "erp.integration.mes", name = "mode", havingValue = "stub", matchIfMissing = true)
public class MesClientStub implements MesClient {

    @Override
    public void pushProductionOrder(MesProductionOrderDTO dto) {
        log.info("[MES-STUB] 生产订单下推: orderNo={}, material={}, qty={}",
                dto.getOrderNo(), dto.getMaterialCode(), dto.getQty());
    }

    @Override
    public void pushMaterial(MesMaterialDTO dto) {
        log.info("[MES-STUB] 物料下推: code={}, name={}", dto.getCode(), dto.getName());
    }

    @Override
    public void pushBom(MesBomDTO dto) {
        log.info("[MES-STUB] BOM下推: material={}, version={}, items={}",
                dto.getMaterialCode(), dto.getVersion(), dto.getItems().size());
    }

    @Override
    public void pushRouting(MesRoutingDTO dto) {
        log.info("[MES-STUB] 工艺路线下推: routingCode={}, steps={}",
                dto.getRoutingCode(), dto.getSteps().size());
    }
}
