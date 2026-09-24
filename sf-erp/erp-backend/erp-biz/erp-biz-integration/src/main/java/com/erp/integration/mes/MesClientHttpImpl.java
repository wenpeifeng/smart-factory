package com.erp.integration.mes;

import com.erp.integration.config.IntegrationProperties;
import com.erp.integration.dto.MesBomDTO;
import com.erp.integration.dto.MesMaterialDTO;
import com.erp.integration.dto.MesProductionOrderDTO;
import com.erp.integration.dto.MesRoutingDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * MES 出站客户端真实实现（http 模式）。
 * <p>POST 到 MES 入站 {@code /integration/erp/**}，请求头
 * {@code X-Integration-Token}（值 = erp.integration.mes.outbound-token，
 * 与 MES 侧 mes.integration.erp.inbound-token 对齐，默认 erp-integration-token）。
 * 非 2xx 响应抛 RestClientResponseException，由 OutboxService 统一退避重试。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "erp.integration.mes", name = "mode", havingValue = "http")
public class MesClientHttpImpl implements MesClient {

    /** MES 入站令牌请求头（与 MES 侧 ErpInboundController 约定一致） */
    static final String TOKEN_HEADER = "X-Integration-Token";

    private final RestClient restClient;

    public MesClientHttpImpl(IntegrationProperties properties) {
        IntegrationProperties.Mes mes = properties.getMes();
        this.restClient = RestClient.builder()
                .baseUrl(mes.getBaseUrl())
                .defaultHeader(TOKEN_HEADER, mes.getOutboundToken())
                .build();
    }

    @Override
    public void pushProductionOrder(MesProductionOrderDTO dto) {
        restClient.post().uri("/integration/erp/orders")
                .body(dto).retrieve().toBodilessEntity();
    }

    @Override
    public void pushMaterial(MesMaterialDTO dto) {
        restClient.post().uri("/integration/erp/materials")
                .body(dto).retrieve().toBodilessEntity();
    }

    @Override
    public void pushBom(MesBomDTO dto) {
        restClient.post().uri("/integration/erp/boms")
                .body(dto).retrieve().toBodilessEntity();
    }

    @Override
    public void pushRouting(MesRoutingDTO dto) {
        restClient.post().uri("/integration/erp/routings")
                .body(dto).retrieve().toBodilessEntity();
    }
}
