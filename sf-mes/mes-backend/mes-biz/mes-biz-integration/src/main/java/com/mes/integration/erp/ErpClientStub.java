package com.mes.integration.erp;

import com.mes.integration.erp.dto.ErpPickingDTO;
import com.mes.integration.erp.dto.ErpOrderStatusDTO;
import com.mes.integration.erp.dto.ErpQualityResultDTO;
import com.mes.integration.erp.dto.ErpReturnDTO;
import com.mes.integration.erp.dto.ErpWorkReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ERP 出站客户端契约桩。
 * <p>ERP 侧开放接口未就绪前的默认实现：仅记录日志并视为推送成功，
 * 使回传链路（推送日志/重试/对账）可先行联调。
 * ERP 就绪后置 {@code mes.integration.erp.mode=http} 切换真实客户端。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mes.integration.erp", name = "mode", havingValue = "stub", matchIfMissing = true)
public class ErpClientStub implements ErpClient {

    @Override
    public void pushWorkReport(ErpWorkReportDTO dto) {
        log.info("[ERP-STUB] 完工报工回传: woNo={}, operation={}, goodQty={}, scrapQty={}",
                dto.getWoNo(), dto.getOperationName(), dto.getGoodQty(), dto.getScrapQty());
    }

    @Override
    public void pushPicking(ErpPickingDTO dto) {
        log.info("[ERP-STUB] 领料回传: pickNo={}, woNo={}, items={}", dto.getPickNo(), dto.getWoNo(), dto.getItems().size());
    }

    @Override
    public void pushReturn(ErpReturnDTO dto) {
        log.info("[ERP-STUB] 退料回传: returnNo={}, woNo={}, items={}", dto.getReturnNo(), dto.getWoNo(), dto.getItems().size());
    }

    @Override
    public void pushQualityResult(ErpQualityResultDTO dto) {
        log.info("[ERP-STUB] 质检结果回传: inspectNo={}, type={}, conclusion={}",
                dto.getInspectNo(), dto.getInspectType(), dto.getConclusion());
    }

    @Override
    public void pushOrderStatus(ErpOrderStatusDTO dto) {
        log.info("[ERP-STUB] 工单进度镜像: woNo={}, status={}", dto.getWoNo(), dto.getMesStatus());
    }
}
