package com.erp.org.mapper;

import com.erp.org.dto.raw.CostBalanceRaw;
import org.apache.ibatis.annotations.Param;

/**
 * 存货计价流水读取 Mapper（cost_flow，cost 模块约定表，只读）。
 * <p>
 * 组织间调拨的「调出方最近加权价」取该物料最新一行运行余额：
 * 单价 = balance_cost / balance_qty。表由 V801 创建；模块启动早期或表尚未
 * 建成时由 Service 层捕获 SQL 异常优雅降级为单价 0（见文档 org.md 说明）。
 */
public interface OrgCostFlowMapper {

    /**
     * 取物料最新一行计价流水运行余额（按 id 倒序第一条），无数据返回 null。
     */
    CostBalanceRaw selectLatestBalance(@Param("materialId") Long materialId);
}
