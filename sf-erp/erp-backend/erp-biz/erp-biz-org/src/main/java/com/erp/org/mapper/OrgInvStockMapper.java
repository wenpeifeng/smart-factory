package com.erp.org.mapper;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 即时库存过账 Mapper（inv_stock，inventory 模块约定表）。
 * <p>
 * 组织间调拨不 import inventory 代码，按 docs/modules/inventory.md「过渡期兼容」约定
 * 裸 SQL 过账，且只钉死 batch_no='' 汇总行（批次物料请在库存模块批次出库，见 org.md 边界）：
 * <ul>
 *   <li>扣减：条件 UPDATE 带 {@code qty - ? >= 0}（防负库存）与 {@code deleted = 0}，
 *       影响行数 0 一律按“库存不足”抛 BusinessException 回滚；</li>
 *   <li>增加：先常规 UPDATE，命中 0 行再 UPSERT 兜底——ON DUPLICATE KEY 不依赖
 *       uk_stock 的具体列结构（一期三列/二期四列批次结构均兼容）。</li>
 * </ul>
 */
public interface OrgInvStockMapper {

    /**
     * 调出仓扣库存（行锁 + 防负库存条件，仅 batch_no='' 汇总行）。
     *
     * @param qty 扣减数量（正数）
     * @return 影响行数；0 = 行不存在或余量不足（调用方按库存不足回滚）
     */
    int deductStock(@Param("warehouseId") Long warehouseId,
                    @Param("materialId") Long materialId,
                    @Param("qty") BigDecimal qty);

    /**
     * 调入仓加库存（常规 UPDATE，仅 batch_no='' 汇总行）。
     *
     * @return 影响行数；0 = 汇总行不存在，调用方走 {@link #upsertIncreaseStock}
     */
    int increaseStock(@Param("warehouseId") Long warehouseId,
                      @Param("materialId") Long materialId,
                      @Param("qty") BigDecimal qty);

    /**
     * 调入仓加库存 UPSERT（并发首次插入撞唯一键时由数据库兜底合并）。
     *
     * @return 1=新插入；2=命中唯一键转 UPDATE；调用方不区分
     */
    int upsertIncreaseStock(@Param("warehouseId") Long warehouseId,
                            @Param("materialId") Long materialId,
                            @Param("qty") BigDecimal qty,
                            @Param("userId") Long userId);
}
