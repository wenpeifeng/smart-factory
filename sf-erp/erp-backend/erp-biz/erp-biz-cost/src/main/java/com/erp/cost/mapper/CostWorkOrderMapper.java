package com.erp.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.cost.entity.CostWorkOrder;
import org.apache.ibatis.annotations.Param;

/**
 * 工单成本归集 Mapper（费/工时手工分摊部分 + 订单快照）。
 */
public interface CostWorkOrderMapper extends BaseMapper<CostWorkOrder> {

    /** 按生产订单 ID 取归集行（不加锁，手工录入低频操作由 uk 兜底唯一性） */
    CostWorkOrder selectByWorkOrderId(@Param("workOrderId") Long workOrderId);
}
