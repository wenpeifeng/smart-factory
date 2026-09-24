package com.mes.material.service;

import com.mes.common.result.PageResult;
import com.mes.material.dto.vo.ShopStockVO;

/**
 * 线边仓库存服务（领退料的库存账；查询供齐套/看板）。
 */
public interface ShopStockService {

    /**
     * 线边仓入库（无记录则新建；并发安全由唯一键 + 条件更新保证）。
     *
     * @param materialId   物料 id
     * @param batchNo      批次（空串表示不管理批次）
     * @param workcenterId 工作中心 id
     * @param qty          入库数量
     */
    void stockIn(Long materialId, String batchNo, Long workcenterId, java.math.BigDecimal qty);

    /**
     * 线边仓出库（不足抛 BusinessException）。
     *
     * @param materialId   物料 id
     * @param batchNo      批次
     * @param workcenterId 工作中心 id
     * @param qty          出库数量
     */
    void stockOut(Long materialId, String batchNo, Long workcenterId, java.math.BigDecimal qty);

    /**
     * 分页查询线边仓库存。
     *
     * @param pageNum     页码
     * @param pageSize    页大小
     * @param keyword     物料编码/名称模糊
     * @param workcenterId 工作中心过滤
     * @return 分页结果
     */
    PageResult<ShopStockVO> page(long pageNum, long pageSize, String keyword, Long workcenterId);
}
