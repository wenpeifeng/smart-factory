package com.mes.plan.service;

import java.math.BigDecimal;

/**
 * 工单用料需求服务（领料审核回写已领数量的入口，material 模块调用）。
 */
public interface WoMaterialService {

    /**
     * 累加工单某物料的已领数量（无该物料需求行时报错）。
     *
     * @param workorderId 工单 id
     * @param materialId  物料 id
     * @param qty         本次领料数量
     */
    void addIssued(Long workorderId, Long materialId, BigDecimal qty);
}
