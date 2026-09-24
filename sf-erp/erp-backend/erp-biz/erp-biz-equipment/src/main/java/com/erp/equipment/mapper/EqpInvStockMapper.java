package com.erp.equipment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 即时库存扣减原语 —— 设备模块对约定表 inv_stock 的唯一写入口。
 * <p>
 * 与库存模块 {@code InvStockMapper.deductStock} 语义完全一致（并行开发约定，不互相 import）：
 * 条件 UPDATE 行锁扣减，{@code qty + delta >= 0} 才生效，影响行数 0 一律按
 * 「库存不足」抛业务异常（行不存在与余额不足同语义）。本模块当前只有备件出库场景，
 * 故只实现扣减原语；入库（EQP_IN）业务出现时再补充 increase/upsert。
 */
@Mapper
public interface EqpInvStockMapper {

    /**
     * 条件扣减库存：qty = qty + delta（delta 为负数），仅当扣后 &gt;= 0 生效，version+1。
     *
     * @param warehouseId 仓库 ID
     * @param materialId  物料 ID
     * @param delta       负数增量
     * @return 影响行数（0 = 无库存行或余额不足，调用方按"库存不足"处理）
     */
    int deductStock(@Param("warehouseId") Long warehouseId,
                    @Param("materialId") Long materialId,
                    @Param("delta") BigDecimal delta);
}
