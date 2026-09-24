package com.erp.inventory.service;

import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存服务 —— 全系统唯一的库存入口（erp-inventory 模块对外暴露的核心 SPI）。
 * <p>
 * 职责：维护 inv_stock（即时库存）与 inv_movement（库存流水）两个表的强一致，
 * 保证不变量「库存永不透支（qty &gt;= 0）」+「每次变动必有流水」。
 * <p>
 * 使用方（同 JVM 单体，直接 @Autowired 注入本接口）：
 * <ul>
 *   <li>采购模块：采购入库 {@code changeStock(wh, mat, +qty, PUR_IN, ...)},
 *       采购退货 {@code changeStock(wh, mat, -qty, PUR_RET, ...)}；</li>
 *   <li>销售模块：销售出库 SAL_OUT（负数）、销售退货 SAL_RET（正数）；</li>
 *   <li>生产模块：生产入库 PROD_IN、生产领料 PROD_USE；</li>
 *   <li>本模块：其他出入库单审核 OTH_IN/OTH_OUT、盘点调平 ADJUST、调拨 MOVE。</li>
 * </ul>
 * 所有方法自带事务（@Transactional）；若调用方已有事务则加入同一事务
 * （例如"审核单据+过账库存"要么全成要么全回滚）。
 * <p>
 * <b>二期批次扩展（V411 起）</b>：inv_stock 唯一键升级为
 * (warehouse_id, material_id, batch_no, deleted)。启用批次管理
 * （base_material.batch_managed=1）的物料走 {@link #stockIn}/{@link #stockOut}
 * 批次接口；非批次物料与一期调用路径（changeStock 旧签名）永远只操作
 * batch_no='' 汇总行，行为与一期完全一致。
 * <p>
 * <b>三期 WMS 库位维度扩展（V421 起）</b>：inv_stock 唯一键升维为
 * (warehouse_id, material_id, batch_no, location_id, deleted)，location_id=0
 * 表示「汇总/未上架行」（= 二期口径的批次行，历史行为不变），location_id&gt;0
 * 表示「库位行」——真实库存行（仅批次物料落库位行；上架=未上架行扣+库位行加同事务双写，
 * 总量守恒、不产生流水）。业务出库策略候选行=未上架行优先再库位行（出库仍优先从汇总扣）。
 * 四期效期扩展（V422）：批次行带 expiry_date，FEFO 策略按效期升序出库。
 */
public interface StockService {

    /**
     * 库存变动（全系统库存过账的唯一入口）。
     * <p>
     * 实现：行锁方式 UPSERT inv_stock（存在则条件 UPDATE，不存在且入库则 INSERT
     * 兜底并发）+ INSERT inv_movement，整体一个事务。
     * <p>
     * 二期语义：固定操作 batch_no='' 汇总行（非批次物料的标准路径）。
     * 批次物料的出入库请改用 {@link #stockIn}/{@link #stockOut}。
     *
     * @param warehouseId 仓库 ID（必填）
     * @param materialId  物料 ID（必填）
     * @param qty         变动数量：正数=入库，负数=出库（不允许 0）
     * @param bizType     业务类型（PUR_IN/PUR_RET/SAL_OUT/SAL_RET/OTH_IN/OTH_OUT/ADJUST/MOVE/PROD_IN/PROD_USE）
     * @param bizId       来源单据 ID（无单据的裸操作可传 null）
     * @param bizNo       来源单号（采购入库传采购单号等，用于流水追溯，可传 null）
     * @throws com.erp.common.exception.BusinessException 数量为 0 / 参数为空 /
     *                                                    出库数量超过现存量（"库存不足"，事务回滚）
     */
    void changeStock(Long warehouseId, Long materialId, BigDecimal qty,
                     InvBizType bizType, Long bizId, String bizNo);

    /**
     * 库存变动（可带备注的重载，备注落 inv_movement.remark）。
     *
     * @see #changeStock(Long, Long, BigDecimal, InvBizType, Long, String)
     */
    void changeStock(Long warehouseId, Long materialId, BigDecimal qty,
                     InvBizType bizType, Long bizId, String bizNo, String remark);

    /**
     * 仓库调拨：调出仓出库 + 调入仓入库，两条 MOVE 流水共用同一 bizNo，同一事务。
     * <p>
     * 二期语义：调 batch_no='' 汇总行（与一期一致）；批次物料的调拨
     * 请按批次分别调 stockOut(BATCH)+stockIn 组合。
     *
     * @param fromWarehouseId 调出仓库 ID
     * @param toWarehouseId   调入仓库 ID（不得与调出仓库相同）
     * @param materialId      物料 ID
     * @param qty             调拨数量（正数）
     * @param bizNo           调拨单号（TR+日期+流水；调用方无单号体系时可传 null，内部生成）
     * @throws com.erp.common.exception.BusinessException 两仓相同 / 数量非正 /
     *                                                    调出仓库存不足（整个调拨回滚）
     */
    void transfer(Long fromWarehouseId, Long toWarehouseId, Long materialId,
                  BigDecimal qty, String bizNo);

    // ==================== 二期批次扩展（V411 起） ====================

    /**
     * 批次维度入库（批次物料的入库入口；非批次物料等价于 changeStock 正数路径）。
     * <p>
     * 规则（按 base_material.batch_managed 校验，服务端强约束）：
     * <ul>
     *   <li>批次物料：batchNo 必填（空白视为未指定，抛 BusinessException）；</li>
     *   <li>非批次物料：batchNo 强制置 ''（传了也忽略），落汇总行；</li>
     *   <li>批次行首次出现时写 first_in_at=NOW()（此后续入不回写，保持"最早入库时间"）。</li>
     * </ul>
     *
     * @param warehouseId 仓库 ID（必填）
     * @param materialId  物料 ID（必填）
     * @param batchNo     批次号（批次物料必填；非批次物料忽略）
     * @param qty         入库数量（必须大于 0）
     * @param bizType     业务类型（PUR_IN/OTH_IN/SAL_RET/PROD_IN/ADJUST...）
     * @param bizId       来源单据 ID（可 null）
     * @param bizNo       来源单号（用于流水追溯）
     * @throws com.erp.common.exception.BusinessException 物料不存在 / 批次物料未指定批次号 /
     *                                                    数量非正
     */
    void stockIn(Long warehouseId, Long materialId, String batchNo, BigDecimal qty,
                 InvBizType bizType, Long bizId, String bizNo);

    /**
     * 批次维度入库（可带流水备注的重载，备注落 inv_movement.remark）。
     *
     * @see #stockIn(Long, Long, String, BigDecimal, InvBizType, Long, String)
     */
    void stockIn(Long warehouseId, Long materialId, String batchNo, BigDecimal qty,
                 InvBizType bizType, Long bizId, String bizNo, String remark);

    /**
     * 策略出库（批次物料的出库入口；非批次物料无论何种策略都走 batch_no='' 汇总行原逻辑）。
     * <p>
     * 策略语义（仅对批次物料生效，见 {@link OutStrategy}）：
     * <ul>
     *   <li>{@code FIFO}：按批次 first_in_at 升序依次扣减，不够自动跨批，
     *       每个批次一行流水（同一 bizNo）——一次出库可能拆多行；</li>
     *   <li>{@code BATCH}：只扣指定 batchNo 的批次行，不足整体回滚；</li>
     *   <li>{@code AVG}：总量扣减（跨批次行按 id 序扣足即可，不按入库时间排序），
     *       流水仍按实际扣减批次逐行记录。</li>
     * </ul>
     * 并发：FIFO/AVG 先 FOR UPDATE 锁定候选批次行再逐批条件扣减，防超卖；
     * 任一批次扣减失败整体回滚（不存在"扣一半"的中间态）。
     *
     * @param warehouseId 仓库 ID（必填）
     * @param materialId  物料 ID（必填）
     * @param qty         出库数量（必须大于 0，接口按正数收）
     * @param strategy    出库策略（null 视为 AVG）
     * @param batchNo     指定批次号（仅 strategy=BATCH 必填；批次物料 BATCH 策略下为空抛异常；
     *                    非批次物料忽略）
     * @param bizType     业务类型（OTH_OUT/SAL_OUT/PUR_RET/PROD_USE/ADJUST/MOVE...）
     * @param bizId       来源单据 ID（可 null）
     * @param bizNo       来源单号（拆批的多行流水共用同一 bizNo）
     * @param remark      流水备注（可 null；拆批时自动追加"批次:xxx"后缀）
     * @return 实际扣减明细（按扣减顺序，一batch一元素；非批次物料返回单个 batchNo='' 元素）
     * @throws com.erp.common.exception.BusinessException 物料不存在 / 数量非正 /
     *                    BATCH 未指定批次 / 指定批次库存不足 / 总库存不足（整体回滚）
     */
    List<DeductionLeg> stockOut(Long warehouseId, Long materialId, BigDecimal qty,
                                OutStrategy strategy, String batchNo,
                                InvBizType bizType, Long bizId, String bizNo, String remark);

    /**
     * 出库扣减明细：一个批次一条（FIFO 跨批时一次出库返回多个元素）。
     * 调用方（如单据审核）可据此向用户展示实际扣了哪些批次。
     * V421 起含 locationId（实际扣减的库位行，0=未上架汇总行）。
     */
    record DeductionLeg(String batchNo, BigDecimal qty, Long locationId) {
    }

    // ==================== 三期 WMS 库位维度扩展（V421 起） ====================

    /**
     * 库位维度入库（可带效期，V421/V422）——批次物料直接入库到指定库位行的入口。
     * <p>
     * 规则：
     * <ul>
     *   <li>locationId 为 null/0 = 未上架汇总行（等价于 {@link #stockIn(Long, Long, String, BigDecimal, InvBizType, Long, String, String)}）；</li>
     *   <li>locationId &gt; 0 = 库位行（仅批次物料允许，非批次物料强制回汇总行）；
     *       库位行首插写 first_in_at；</li>
     *   <li>expiryDate 非空时写入行效期（行已存在且效期为空则回填，已有值不覆盖）；
     *       为空且 base_material.shelf_life_days 非空时自动推算 = 入库日 + 保质期天数；</li>
     *   <li>同事务落 inv_movement（location_id 随行携带）。</li>
     * </ul>
     *
     * @param warehouseId 仓库 ID（必填）
     * @param materialId  物料 ID（必填）
     * @param batchNo     批次号（批次物料必填）
     * @param locationId  库位 ID（null/0=未上架汇总行）
     * @param qty         入库数量（必须大于 0）
     * @param expiryDate  效期（可 null；null 且物料有保质期时自动推算）
     * @throws com.erp.common.exception.BusinessException 物料不存在 / 批次物料未指定批次 /
     *                    非批次物料指定库位 / 数量非正
     */
    void stockInAt(Long warehouseId, Long materialId, String batchNo, Long locationId,
                   BigDecimal qty, InvBizType bizType, Long bizId, String bizNo,
                   String remark, LocalDate expiryDate);

    /**
     * 库位维度精确行出库（V421）——只扣 (仓库, 物料, 批次, 库位) 定位的那一行库存。
     * <p>
     * 用于 WMS 手工拣选任务（无来源单据、账面与实物同扣）与按库位直接出库场景；
     * 与 {@link #stockOut} 的策略扣减互补（后者按策略选行，本方法行由调用方指定）。
     * 同事务落 inv_movement（location_id=该行库位）。
     *
     * @param locationId 库位 ID（null/0=未上架汇总行）
     * @throws com.erp.common.exception.BusinessException 行不存在或余额不足（"指定库位库存不足"，整体回滚）
     */
    DeductionLeg stockOutAtLocation(Long warehouseId, Long materialId, String batchNo, Long locationId,
                                    BigDecimal qty, InvBizType bizType, Long bizId, String bizNo,
                                    String remark);

    /**
     * 库位行间转移（V421）——WMS 上架/移库/补货的库存动作：
     * fromLocationId 行扣减 + toLocationId 行增加，同事务双写，总量守恒。
     * <p>
     * ★ 口径：本方法是纯实物挪动（未上架↔库位行、库位↔库位），账面总量与
     * inv_movement 流水均不变——业务过账流水只在单据审核时产生一次，
     * 库内挪动不再产生流水，避免收发存报表双计。库位行效期从来源行复制。
     *
     * @param fromLocationId 调出库位（null/0=未上架汇总行）
     * @param toLocationId   调入库位（null/0=未上架汇总行，等于回退上架）
     * @throws com.erp.common.exception.BusinessException 两库位相同 / 调出行库存不足 /
     *                    非批次物料指定库位（库位行仅批次物料）
     */
    void transferLocation(Long warehouseId, Long materialId, String batchNo,
                          Long fromLocationId, Long toLocationId, BigDecimal qty, String bizNo);

    /**
     * 查询某仓库+物料+批次在指定库位行的现存量（0=未上架行；行不存在返回 null）。
     * WMS 任务引擎判断库位库存/未上架余量用。
     *
     * @return 库存行（含 qty/expiry/firstInAt）；无行返回 null
     */
    com.erp.inventory.entity.InvStock getStockRow(Long warehouseId, Long materialId,
                                                  String batchNo, Long locationId);

    /**
     * 查询某仓库+物料的可用现存量（跨全部批次/库位行求和，V621 新增）。
     * <p>
     * 供跨模块业务（如 MRP 倒冲领料的主料/替代料分配）在出库前做
     * "多物料择优扣减"规划使用；返回值仅作参考快照，真正扣减仍必须走
     * {@link #stockOut}（条件扣减防超卖，快照与扣减之间的并发差额由
     * stockOut 的行锁兜底，不足时整体回滚）。
     *
     * @param warehouseId 仓库 ID
     * @param materialId  物料 ID
     * @return 可用量（无库存行返回 0，不抛异常）
     */
    java.math.BigDecimal getAvailableQty(Long warehouseId, Long materialId);
}
