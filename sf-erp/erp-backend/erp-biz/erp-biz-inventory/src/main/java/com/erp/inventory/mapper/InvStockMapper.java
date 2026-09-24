package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.StockQuery;
import com.erp.inventory.dto.vo.BatchStockVO;
import com.erp.inventory.dto.vo.StockVO;
import com.erp.inventory.dto.vo.WarningVO;
import com.erp.inventory.entity.InvStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 即时库存 Mapper。
 * <p>
 * 库存增减原语（行锁方案，供 StockServiceImpl 独占使用）分三组：
 * <ul>
 *   <li><b>非批次组（一期语义，batch_no='' 且 location_id=0 汇总行）</b>：
 *       {@link #increaseStock} / {@link #upsertIncrease} / {@link #deductStock}
 *       —— WHERE 显式钉死 batch_no='' AND location_id=0，保证一期调用路径
 *       （changeStock 旧签名、采购/销售迁移过渡）永远只命中汇总行；</li>
 *   <li><b>批次组（V411 二期）</b>：
 *       {@link #increaseBatchStock} / {@link #upsertIncreaseBatch} / {@link #deductBatchStock}
 *       —— 以 (仓库, 物料, 批次, 库位) 定位库存行（V421 起带 locationId，0=未上架行）；
 *       FIFO/FEFO/AVG 先经 {@link #selectBatchRowsForUpdate} 行锁快照后再逐行条件扣减；</li>
 *   <li><b>查询组</b>：即时库存分页 / 预警分页 / 批次余额清单（V421 起按批次聚合含库位行）。</li>
 * </ul>
 */
@Mapper
public interface InvStockMapper extends BaseMapper<InvStock> {

    /**
     * 常规加库存（batch_no='' 汇总行，location_id=0）：qty = qty + delta。
     *
     * @return 影响行数（0 表示该仓库+物料还没有汇总行）
     */
    int increaseStock(@Param("warehouseId") Long warehouseId,
                      @Param("materialId") Long materialId,
                      @Param("delta") BigDecimal delta);

    /**
     * 插入式加库存（UPSERT，batch_no='' 汇总行，location_id=0）：首次出现库存行或并发插入撞唯一键时兜底。
     *
     * @return 影响行数（1=新插入，2=走了重复键更新）
     */
    int upsertIncrease(@Param("warehouseId") Long warehouseId,
                       @Param("materialId") Long materialId,
                       @Param("delta") BigDecimal delta);

    /**
     * 条件扣减（batch_no='' 汇总行，location_id=0）：仅当扣后 &gt;= 0 才生效。
     *
     * @return 影响行数（0 表示汇总行不存在或余额不足，调用方一律按"库存不足"处理）
     */
    int deductStock(@Param("warehouseId") Long warehouseId,
                    @Param("materialId") Long materialId,
                    @Param("delta") BigDecimal delta);

    // ==================== 批次原语（V411 二期 + V421 库位维度） ====================

    /**
     * 批次行常规加库存（不回写 first_in_at，保持最早入库时间）。
     *
     * @param locationId 库位（0=未上架行，&gt;0=库位行）
     * @return 影响行数（0 表示该 (仓库,物料,批次,库位) 行尚不存在）
     */
    int increaseBatchStock(@Param("warehouseId") Long warehouseId,
                           @Param("materialId") Long materialId,
                           @Param("batchNo") String batchNo,
                           @Param("locationId") Long locationId,
                           @Param("delta") BigDecimal delta);

    /**
     * 批次行 UPSERT 加库存：首次出现批次行时写入 first_in_at（库位行同时写效期），
     * 撞唯一键（并发首插）时只累加 qty、不回写 first_in_at/expiry_date。
     *
     * @param locationId  库位（0=未上架行，&gt;0=库位行）
     * @param expiryDate  效期（可 null；首插写入，库位行上架转移时由调用方从来源行复制）
     */
    int upsertIncreaseBatch(@Param("warehouseId") Long warehouseId,
                            @Param("materialId") Long materialId,
                            @Param("batchNo") String batchNo,
                            @Param("locationId") Long locationId,
                            @Param("expiryDate") LocalDate expiryDate,
                            @Param("delta") BigDecimal delta);

    /**
     * 批次行条件扣减：仅当扣后 &gt;= 0 才生效。
     *
     * @param locationId 库位（0=未上架行，&gt;0=库位行）
     * @return 影响行数（0 = 行不存在或余额不足）
     */
    int deductBatchStock(@Param("warehouseId") Long warehouseId,
                         @Param("materialId") Long materialId,
                         @Param("batchNo") String batchNo,
                         @Param("locationId") Long locationId,
                         @Param("delta") BigDecimal delta);

    /**
     * 回填行效期（仅当现值为 NULL 时写入，已有值不覆盖）。
     * V422：入库带效期但行已存在（历史批次行）时补写，保证 FEFO 有据可依。
     */
    int updateExpiryIfNull(@Param("warehouseId") Long warehouseId,
                           @Param("materialId") Long materialId,
                           @Param("batchNo") String batchNo,
                           @Param("locationId") Long locationId,
                           @Param("expiryDate") LocalDate expiryDate);

    /**
     * 行锁读取某仓库+物料的全部可扣批次行（qty &gt; 0，含未上架行与库位行）。
     * <p>
     * FIFO/FEFO/AVG 策略先本查询锁定候选行，再在事务内逐行条件扣减，
     * 防止「按快照算好拆分方案后并发被别人先扣」导致的超卖。
     * 必须在事务内调用（FOR UPDATE）。
     * 排序口径（V421）：未上架行（location_id=0）优先——「出库仍从汇总行扣」，
     * 同组内再按策略排序（FIFO=first_in_at / FEFO=expiry_date+first_in_at / AVG=id）。
     *
     * @param orderStrategy FIFO/FEFO/AVG（其余值按 AVG 处理）
     */
    List<InvStock> selectBatchRowsForUpdate(@Param("warehouseId") Long warehouseId,
                                            @Param("materialId") Long materialId,
                                            @Param("orderStrategy") String orderStrategy);

    /**
     * 查询单条库存行（无锁，读余量/效期用；WMS 任务引擎与上架转移用）。
     *
     * @param locationId 库位（0=未上架行）
     * @return 库存行；不存在返回 null
     */
    InvStock selectStockRow(@Param("warehouseId") Long warehouseId,
                            @Param("materialId") Long materialId,
                            @Param("batchNo") String batchNo,
                            @Param("locationId") Long locationId);

    /**
     * 查询物料的批次管理标志（base_material.batch_managed：1=批次物料 0=普通物料）。
     * <p>
     * 延续一期「跨模块只 LEFT JOIN 表、不 import basedata 代码」的约定。
     *
     * @return 1=批次物料；0=非批次物料；null=物料不存在（调用方按校验失败处理）
     */
    Integer selectBatchManaged(@Param("materialId") Long materialId);

    /**
     * 查询物料保质期天数（base_material.shelf_life_days，V422；无则 null）。
     * 入库未显式传效期时按「入库日 + 天数」自动推算。
     */
    Integer selectShelfLifeDays(@Param("materialId") Long materialId);

    // ==================== 查询 ====================

    /**
     * 即时库存分页（LEFT JOIN 基础资料补编码/名称/安全库存；V421 起带库位编码）。
     */
    IPage<StockVO> selectStockPage(IPage<StockVO> page, @Param("q") StockQuery query);

    /**
     * 安全库存预警分页（以物料为主，无库存行按 0 计）。
     */
    IPage<WarningVO> selectWarningPage(IPage<WarningVO> page, @Param("q") StockQuery query);

    /**
     * 批次余额清单（V421 起按 (仓库,物料,批次) 聚合——未上架行+库位行合计，
     * 效期取批次内最早、首入取最早），供「批次追溯页 / 移动端拣货建议」使用；
     * FIFO 语义按 first_in_at 升序、FEFO 语义按 expiry 升序（由前端按需切换展示）。
     *
     * @param materialId  物料 ID（必填）
     * @param warehouseId 仓库 ID（可选，不传=全部仓库）
     */
    List<BatchStockVO> selectBatchList(@Param("materialId") Long materialId,
                                       @Param("warehouseId") Long warehouseId);
}
