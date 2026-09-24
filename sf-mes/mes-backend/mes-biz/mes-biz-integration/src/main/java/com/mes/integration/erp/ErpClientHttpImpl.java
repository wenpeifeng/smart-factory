package com.mes.integration.erp;

import com.mes.common.exception.BusinessException;
import com.mes.integration.config.IntegrationProperties;
import com.mes.integration.erp.dto.ErpOrderStatusDTO;
import com.mes.integration.erp.dto.ErpPickingDTO;
import com.mes.integration.erp.dto.ErpQualityResultDTO;
import com.mes.integration.erp.dto.ErpReturnDTO;
import com.mes.integration.erp.dto.ErpWorkReportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * ERP 出站客户端 http 实现（mode=http）。
 * <p>契约见 sf-erp docs/modules/mes-integration.md：POST {base}/integration/mes/**
 * 携带 X-Integration-Token（= erp.integration.mes.inbound-token）；ERP 侧按
 * bizType+bizKey 幂等（int_message_log），重复推送返回 DUPLICATE 仍算成功。
 * 失败抛异常交由 ErpPushService 的推送日志重试机制补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mes.integration.erp", name = "mode", havingValue = "http")
public class ErpClientHttpImpl implements ErpClient {

    private final IntegrationProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public void pushWorkReport(ErpWorkReportDTO dto) {
        post("/integration/mes/work-reports", dto, "完工报工");
    }

    @Override
    public void pushPicking(ErpPickingDTO dto) {
        post("/integration/mes/pickings", dto, "领料");
    }

    @Override
    public void pushReturn(ErpReturnDTO dto) {
        post("/integration/mes/returns", dto, "退料");
    }

    @Override
    public void pushQualityResult(ErpQualityResultDTO dto) {
        post("/integration/mes/quality-results", dto, "质检结果");
    }

    @Override
    public void pushOrderStatus(ErpOrderStatusDTO dto) {
        post("/integration/mes/order-status", dto, "工单进度");
    }

    private void post(String path, Object body, String scene) {
        try {
            String resp = restClient.post()
                    .uri(properties.getErp().getBaseUrl() + path)
                    .header("X-Integration-Token", properties.getErp().getOutboundToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("[ERP-PUSH] {} 推送成功: {} -> {}", scene, path, abbreviate(resp));
        } catch (RestClientResponseException e) {
            throw new BusinessException("ERP %s推送失败(%d): %s",
                    scene, e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
