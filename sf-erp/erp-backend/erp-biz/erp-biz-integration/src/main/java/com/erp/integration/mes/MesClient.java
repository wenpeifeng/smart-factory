package com.erp.integration.mes;

import com.erp.integration.dto.MesBomDTO;
import com.erp.integration.dto.MesMaterialDTO;
import com.erp.integration.dto.MesProductionOrderDTO;
import com.erp.integration.dto.MesRoutingDTO;

/**
 * MES 出站客户端（ERP -> MES 下推契约，六类接口之下推两族）。
 * <p>下推四方法：生产订单 / 物料 / BOM / 工艺路线（MES 入站
 * {@code /integration/erp/{orders,materials,boms,routings}}）。
 * 实现：stub（契约桩，默认）/ http（MES 就绪后接入）。
 * 调用方为 {@code OutboxService}，已统一做 outbox + 指数退避重试，实现类无需自行重试。
 */
public interface MesClient {

    /**
     * 下推生产订单（orderNo 幂等）。
     *
     * @param dto 生产订单数据
     */
    void pushProductionOrder(MesProductionOrderDTO dto);

    /**
     * 下推物料主数据（code 幂等 upsert）。
     *
     * @param dto 物料主数据
     */
    void pushMaterial(MesMaterialDTO dto);

    /**
     * 下推 BOM（materialCode+version 幂等 upsert）。
     *
     * @param dto BOM 数据
     */
    void pushBom(MesBomDTO dto);

    /**
     * 下推工艺路线（routingCode 幂等 upsert）。
     *
     * @param dto 工艺路线数据
     */
    void pushRouting(MesRoutingDTO dto);
}
