package com.mes.basedata.service;

import com.mes.basedata.entity.BaseRouting;
import com.mes.basedata.entity.BaseRoutingStep;

import java.util.List;

/**
 * 工艺路线服务（供计划模块展开工序）。
 */
public interface BaseRoutingService {

    /**
     * 查询物料的有效工艺路线（无 ACTIVE 版本返回 null，允许无工艺路线单工序报工）。
     *
     * @param materialId 成品物料 id
     * @return 工艺路线头，无则 null
     */
    BaseRouting findActive(Long materialId);

    /**
     * 查询工艺路线工序步骤（按序号升序）。
     *
     * @param routingId 工艺路线 id
     * @return 步骤列表
     */
    List<BaseRoutingStep> listSteps(Long routingId);
}
