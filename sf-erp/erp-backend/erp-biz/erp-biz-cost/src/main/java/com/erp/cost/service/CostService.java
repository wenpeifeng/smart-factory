package com.erp.cost.service;

import com.erp.cost.dto.vo.MaterialBalanceVO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CostService —— 存货计价核心服务（对外 SPI，全系统唯一计价入口）。
 * <p>
 * 计价方法：移动加权平均法（物料 × 全仓维度）。每次入库按
 * {@code 新单价 = (原数量×原单价 + 本次金额) / (原数量 + 本次数量)} 滚动更新；
 * 出库按当前移动加权单价计算成本。
 * <p>
 * 调用时机（一期模块接线，详见 docs/modules/cost.md）：
 * <ul>
 *   <li>采购入库审核：{@code onStockIn(materialId, "PUR_IN", 入库单号, qty, 入库金额)}；</li>
 *   <li>销售出库审核：{@code onStockOut(materialId, "SAL_OUT", 出库单号, qty)}；</li>
 *   <li>生产领料审核：{@code onStockOut(materialId, "PROD_USE", 领料单号, qty)}；</li>
 *   <li>生产/其他入库（无实际金额时 amount 传 null，按当前加权价估算）。</li>
 * </ul>
 * 存量初始化/补账用 {@code POST /cost/sync}（按 inv_movement 全量重算）或
 * {@code POST /cost/hooks/receive}（单笔补录）。
 * <p>
 * 一致性保证：所有过账方法内部对「该物料最新一行计价流水」加行锁（FOR UPDATE），
 * 同一物料的计价串行执行；已结账（CLOSED）期间拒绝过账。
 */
public interface CostService {

    /**
     * 入库计价过账（移动加权更新物料成本）。
     * <p>
     * 新单价 = (原数量×原单价 + amount) / (原数量 + qty)，写入一行 qty>0 的计价流水。
     *
     * @param materialId 物料 ID（必填）
     * @param bizType    业务类型编码（CostBizType，如 PUR_IN）
     * @param bizNo      来源单号（溯源用）
     * @param qty        入库数量（>0）
     * @param amount     入库金额（≥0；null = 按当前移动加权单价估算，无成本历史按 0）
     * @throws com.erp.common.exception.BusinessException qty≤0 / bizType 非法 /
     *         业务日期所属期间已结账
     */
    void onStockIn(Long materialId, String bizType, String bizNo, BigDecimal qty, BigDecimal amount);

    /** 同 {@link #onStockIn(Long, String, String, BigDecimal, BigDecimal)}，附带仓库（仅溯源）与业务日期（期间依据） */
    void onStockIn(Long materialId, Long warehouseId, String bizType, String bizNo,
                   Long bizId, BigDecimal qty, BigDecimal amount, LocalDate bizDate);

    /**
     * 按当前移动加权单价计算出库成本（纯计算，不过账）。
     * <p>
     * 单价 = 结存金额 / 结存数量；结存数量为 0 时退化为最后一次入账单价；
     * 无任何成本历史返回 0。结果四舍五入 2 位（HALF_UP）。
     *
     * @param materialId 物料 ID
     * @param qty        出库数量（>0）
     * @return 出库成本（金额）
     */
    BigDecimal calcOutCost(Long materialId, BigDecimal qty);

    /**
     * 出库计价过账：按当前移动加权单价计算成本并写入一行 qty<0 的计价流水，
     * 运行余额同步扣减（结存数量扣至 0 时结存金额清零，消除尾差）。
     *
     * @param materialId 物料 ID（必填）
     * @param bizType    业务类型编码（CostBizType，如 SAL_OUT / PROD_USE）
     * @param bizNo      来源单号
     * @param qty        出库数量（>0，台账中记为负数）
     * @return 出库成本金额（= 单价 × 数量）
     * @throws com.erp.common.exception.BusinessException qty≤0 / 出库数量超过账面结存数量 /
     *         bizType 非法 / 期间已结账
     */
    BigDecimal onStockOut(Long materialId, String bizType, String bizNo, BigDecimal qty);

    /** 同 {@link #onStockOut(Long, String, String, BigDecimal)}，附带仓库/单据ID/业务日期 */
    BigDecimal onStockOut(Long materialId, Long warehouseId, String bizType, String bizNo,
                          Long bizId, BigDecimal qty, LocalDate bizDate);

    /**
     * 查询物料当前移动加权成本余额（只读，不加锁；精确过账场景请用 onStockIn/onStockOut）。
     *
     * @param materialId 物料 ID
     * @return 结存数量 / 结存金额 / 移动加权单价（无成本历史时全部为 0）
     */
    MaterialBalanceVO currentBalance(Long materialId);
}
