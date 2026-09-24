package com.mes.integration.service;

import com.mes.integration.erp.dto.ErpBomDTO;
import com.mes.integration.erp.dto.ErpMaterialDTO;
import com.mes.integration.erp.dto.ErpProductionOrderDTO;
import com.mes.integration.erp.dto.ErpRoutingDTO;

/**
 * ERP 入站落地服务（ERP -> MES 下推数据的幂等落库）。
 */
public interface ErpInboundService {

    /**
     * 生产订单下推：幂等建工单（DRAFT，来源 ERP_ORDER）。
     *
     * @param dto 生产订单 DTO（orderNo 幂等）
     * @return 工单 id
     */
    Long receiveProductionOrder(ErpProductionOrderDTO dto);

    /**
     * 物料主数据下推：按 code 幂等 upsert。
     *
     * @param dto 物料 DTO
     * @return 物料 id
     */
    Long receiveMaterial(ErpMaterialDTO dto);

    /**
     * BOM 下推：按 material+version 幂等 upsert（行全删全插，状态 DRAFT）。
     *
     * @param dto BOM DTO
     * @return BOM id
     */
    Long receiveBom(ErpBomDTO dto);

    /**
     * 工艺路线下推：按 routingCode 幂等 upsert（行全删全插，状态 DRAFT）。
     *
     * @param dto 工艺路线 DTO
     * @return 工艺路线 id
     */
    Long receiveRouting(ErpRoutingDTO dto);
}
