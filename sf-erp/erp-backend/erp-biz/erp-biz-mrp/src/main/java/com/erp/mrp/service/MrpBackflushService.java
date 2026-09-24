package com.erp.mrp.service;

import com.erp.mrp.entity.MrpWorkOrder;

import java.math.BigDecimal;

/**
 * 倒冲领料服务（V621）。
 * <p>
 * 工单 pick_type=BACKFLUSH 时，完工报工（良品入库）同事务按
 * BOM 用量 × 本次完工良品数量 × (1+损耗率) 自动生成倒冲领料出库：
 * 主料优先扣减，不足部分按替代组优先级依次用替代料补足
 * （换算：1 单位主料 = ratio 单位替代料）。
 * 出库走 {@link com.erp.inventory.service.StockService#stockOut}（FIFO，PROD_USE），
 * 库存不足（含替代料耗尽仍不足）整单回滚——与现有领料审核策略一致，不允许负库存。
 */
public interface MrpBackflushService {

    /**
     * 完工倒冲：按 BOM×完工良品数量自动扣料并落倒冲领料单（AUDITED）。
     * 必须在完工入库同一事务内调用。
     *
     * @param wo          生产订单（pick_type=BACKFLUSH）
     * @param goodQty     本次完工良品数量
     * @param warehouseId 倒冲扣料仓库（调用方已解析好兜底链）
     * @return 倒冲领料单ID
     * @throws com.erp.common.exception.BusinessException BOM 无子件 /
     *                    主料+全部替代料库存仍不足（整体回滚）
     */
    Long backflush(MrpWorkOrder wo, BigDecimal goodQty, Long warehouseId);
}
