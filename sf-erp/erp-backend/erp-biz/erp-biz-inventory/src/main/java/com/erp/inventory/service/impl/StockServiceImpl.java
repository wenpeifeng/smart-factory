package com.erp.inventory.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.entity.InvMovement;
import com.erp.inventory.entity.InvStock;
import com.erp.inventory.mapper.InvMovementMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.StockService;
import com.erp.inventory.support.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存服务实现 —— 库存守门人。
 * <p>
 * 并发与一致性方案（防超卖/负库存）：
 * <ol>
 *   <li>扣减走条件 UPDATE（行锁）：{@code UPDATE inv_stock SET qty=qty+? WHERE ...
 *       AND qty+? >= 0}，影响行数为 0 一律按"库存不足"抛异常回滚——
 *       无论行不存在还是余额不够，语义相同；</li>
 *   <li>增加先常规 UPDATE，命中 0 行再 {@code INSERT ... ON DUPLICATE KEY UPDATE}
 *       兜底「并发首次插入」撞 uk_stock 唯一键的场景；</li>
 *   <li>每次变动（无论成败后成功）都在同一事务内 INSERT inv_movement，
 *       变更失败则流水随事务一起回滚，保证「库存与流水永远一致」；</li>
 *   <li>version 列随每次 UPDATE +1，仅作为乐观锁辅助排查信息；</li>
 *   <li>批次 FIFO/FEFO/AVG 出库：先 SELECT ... FOR UPDATE 锁定候选批次行，
 *       在锁内计算拆分方案再逐行条件扣减，两笔并发出库不会互相"抢走"已规划的批次。</li>
 * </ol>
 * <p>
 * 批次路由规则（V411 起，按 base_material.batch_managed 分流）：
 * 非批次物料 → batch_no='' 且 location_id=0 汇总行（一期三原语）；
 * 批次物料 → (批次, 库位) 行三原语。
 * 物料不存在 / 批次物料入库未指定批次号，一律抛 BusinessException。
 * <p>
 * <b>V421 库位维度口径（WMS 三期）</b>：
 * <ul>
 *   <li>location_id=0 = 「汇总/未上架行」（一期/二期语义原样保留）；</li>
 *   <li>location_id&gt;0 = 「库位行」= 真实库存行（<b>仅批次物料</b>——非批次物料强制汇总行，
 *       规避 org/equipment 等模块裸 SQL 对 batch_no='' 多行命中的重复扣加风险）；</li>
 *   <li>上架 {@link #transferLocation}：未上架行扣 + 库位行加，同事务双写，总量守恒，
 *       不产生 inv_movement 流水（账面过账只在业务单据审核时发生一次，库内挪动不重复记账）；</li>
 *   <li>业务策略出库候选行：未上架行优先、再库位行（"出库仍从汇总行扣"），
 *       拆行流水逐行携带 location_id。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    /** 批次号最大长度（与 inv_stock.batch_no VARCHAR(50) 对齐） */
    private static final int BATCH_NO_MAX_LEN = 50;

    /** 汇总/未上架行的库位哨兵值 */
    private static final long UNLOCATED = 0L;

    private final InvStockMapper stockMapper;
    private final InvMovementMapper movementMapper;
    private final BizNoGenerator bizNoGenerator;

    // ==================== 一期接口（batch_no='' 且 location_id=0 汇总行，语义不变） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStock(Long warehouseId, Long materialId, BigDecimal qty,
                            InvBizType bizType, Long bizId, String bizNo) {
        changeStock(warehouseId, materialId, qty, bizType, bizId, bizNo, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStock(Long warehouseId, Long materialId, BigDecimal qty,
                            InvBizType bizType, Long bizId, String bizNo, String remark) {
        validateCommon(warehouseId, materialId, qty, bizType);
        // 审计日志：一期通用变动入口（盘盈亏/其他出入库等），与 stockIn/stockOut 同格式
        log.info("[库存][变动] 仓库={} 物料={} 数量={} 业务={} 单号={}",
                warehouseId, materialId, qty.stripTrailingZeros().toPlainString(),
                bizType.getCode(), bizNo);
        changeSummaryRow(warehouseId, materialId, qty, bizType, bizId, bizNo, remark, "");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromWarehouseId, Long toWarehouseId, Long materialId,
                         BigDecimal qty, String bizNo) {
        if (fromWarehouseId == null || toWarehouseId == null || materialId == null) {
            throw BusinessException.ofKey("erp.inv.transfer-required-fields");
        }
        if (fromWarehouseId.equals(toWarehouseId)) {
            throw BusinessException.ofKey("erp.inv.transfer-same-warehouse");
        }
        if (qty == null || qty.signum() <= 0) {
            throw BusinessException.ofKey("erp.inv.transfer-qty-positive");
        }
        // 同一调拨号贯穿两条流水（一出一入），便于流水页配对追溯
        String no = bizNo != null ? bizNo : bizNoGenerator.next("TR");

        // 同类内部调用，事务由本方法 @Transactional 统一保证（batch_no='' 汇总行）
        changeSummaryRow(fromWarehouseId, materialId, qty.negate(), InvBizType.MOVE, null, no,
                "调拨出库", "");
        changeSummaryRow(toWarehouseId, materialId, qty, InvBizType.MOVE, null, no,
                "调拨入库", "");
    }

    // ==================== 二期批次接口（V411 起；V421/V422 内部升级库位+效期） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long warehouseId, Long materialId, String batchNo, BigDecimal qty,
                        InvBizType bizType, Long bizId, String bizNo) {
        stockIn(warehouseId, materialId, batchNo, qty, bizType, bizId, bizNo, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long warehouseId, Long materialId, String batchNo, BigDecimal qty,
                        InvBizType bizType, Long bizId, String bizNo, String remark) {
        // 委托库位维度入口（locationId=0=未上架汇总行；效期自动按保质期推算）
        stockInAt(warehouseId, materialId, batchNo, null, qty, bizType, bizId, bizNo, remark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeductionLeg> stockOut(Long warehouseId, Long materialId, BigDecimal qty,
                                       OutStrategy strategy, String batchNo,
                                       InvBizType bizType, Long bizId, String bizNo, String remark) {
        validateCommon(warehouseId, materialId, qty, bizType);
        if (qty.signum() < 0) {
            throw BusinessException.ofKey("erp.inv.strategy-out-qty-positive");
        }
        OutStrategy st = strategy == null ? OutStrategy.AVG : strategy;
        boolean batchManaged = isBatchManaged(materialId);
        // 审计日志：出库拒绝（库存不足）会回滚上游单据审核，先留痕本次请求参数便于复盘
        log.info("[库存][出库] 仓库={} 物料={} 数量={} 策略={} 批次={} 业务={} 单号={}",
                warehouseId, materialId, qty.stripTrailingZeros().toPlainString(), st,
                batchNo, bizType.getCode(), bizNo);
        if (!batchManaged) {
            if (st == OutStrategy.BATCH && hasText(batchNo)) {
                throw BusinessException.ofKey("erp.biz.batch-not-allowed", materialId);
            }
            // 非批次物料：任何策略都是一期汇总行扣减（非批次物料无库位行，location 恒 0）
            int rows = stockMapper.deductStock(warehouseId, materialId, qty.negate());
            if (rows == 0) {
                // warn：库存不足拒绝是最常见的业务失败，日志带仓库/物料/缺口量便于销售对因
                log.warn("[库存][出库] 拒绝 仓库={} 物料={} 原因=汇总行现存量不足 请求数量={} 单号={}",
                        warehouseId, materialId, qty.stripTrailingZeros().toPlainString(), bizNo);
                throw BusinessException.ofKey("erp.inv.stock-not-enough", warehouseId, materialId, qty.stripTrailingZeros().toPlainString());
            }
            insertMovement(warehouseId, materialId, "", UNLOCATED, qty.negate(), bizType, bizId, bizNo, remark);
            return List.of(new DeductionLeg("", qty, UNLOCATED));
        }

        if (st == OutStrategy.BATCH) {
            // 指定批次：扣该批次行（未上架行优先、再库位行，行锁快照内按序扣足），不足整体回滚
            if (!hasText(batchNo)) {
                throw BusinessException.ofKey("erp.inv.batch-out-batch-required");
            }
            checkBatchNoLength(batchNo);
            List<InvStock> rows = stockMapper.selectBatchRowsForUpdate(
                    warehouseId, materialId, "FIFO");
            BigDecimal remaining = qty;
            List<DeductionLeg> legs = new ArrayList<>();
            for (InvStock row : rows) {
                if (remaining.signum() <= 0) {
                    break;
                }
                if (!row.getBatchNo().equals(batchNo.trim())) {
                    continue;
                }
                BigDecimal take = remaining.min(row.getQty());
                deductLockedRow(warehouseId, materialId, row, take, bizNo);
                legs.add(new DeductionLeg(row.getBatchNo(), take, row.getLocationId()));
                remaining = remaining.subtract(take);
            }
            if (remaining.signum() > 0) {
                log.warn("[库存][出库] 拒绝 仓库={} 物料={} 批次={} 原因=指定批次现存量不足 请求数量={} 单号={}",
                        warehouseId, materialId, batchNo, qty.stripTrailingZeros().toPlainString(), bizNo);
                throw BusinessException.ofKey("erp.inv.batch-stock-not-enough", warehouseId, materialId, batchNo, qty.stripTrailingZeros().toPlainString());
            }
            writeLegMovements(warehouseId, materialId, legs, bizType, bizId, bizNo, remark);
            return legs;
        }

        // FIFO / FEFO / AVG：行锁锁定候选批次行（未上架优先+策略排序）→ 锁内拆分 → 逐行条件扣减
        List<InvStock> rows = stockMapper.selectBatchRowsForUpdate(
                warehouseId, materialId, st == OutStrategy.FIFO || st == OutStrategy.FEFO
                        ? st.name() : "AVG");
        BigDecimal remaining = qty;
        List<DeductionLeg> legs = new ArrayList<>();
        for (InvStock row : rows) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = remaining.min(row.getQty());
            deductLockedRow(warehouseId, materialId, row, take, bizNo);
            legs.add(new DeductionLeg(row.getBatchNo(), take, row.getLocationId()));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            // warn：跨批总量不足，缺口数量是补货/改单决策的关键信息
            log.warn("[库存][出库] 拒绝 仓库={} 物料={} 原因=全部批次现存量不足 请求数量={} 缺口={} 单号={}",
                    warehouseId, materialId, qty.stripTrailingZeros().toPlainString(),
                    remaining.stripTrailingZeros().toPlainString(), bizNo);
            throw BusinessException.ofKey("erp.inv.stock-not-enough-all-batches", warehouseId, materialId, qty.stripTrailingZeros().toPlainString(), remaining.stripTrailingZeros().toPlainString());
        }
        writeLegMovements(warehouseId, materialId, legs, bizType, bizId, bizNo, remark);
        return legs;
    }

    // ==================== 三期 WMS 库位维度接口（V421/V422） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockInAt(Long warehouseId, Long materialId, String batchNo, Long locationId,
                          BigDecimal qty, InvBizType bizType, Long bizId, String bizNo,
                          String remark, LocalDate expiryDate) {
        validateCommon(warehouseId, materialId, qty, bizType);
        if (qty.signum() < 0) {
            throw BusinessException.ofKey("erp.inv.batch-in-qty-positive");
        }
        long loc = normalizeLocation(materialId, locationId);
        // 审计日志：库存是全系统账实一致的根基，每次过账（含来源单号）必须可追溯；
        // 与 inv_movement 流水表互为印证，日志用于无需查库的快速检索（批次为空=汇总行）
        log.info("[库存][入库] 仓库={} 物料={} 批次={} 库位={} 数量={} 效期={} 业务={} 单号={}",
                warehouseId, materialId, batchNo == null || batchNo.isBlank() ? "-" : batchNo.trim(),
                loc, qty.stripTrailingZeros().toPlainString(), expiryDate, bizType.getCode(), bizNo);
        String batch = normalizeBatchForIn(materialId, batchNo);
        LocalDate expiry = resolveExpiry(materialId, expiryDate);
        if (batch.isEmpty()) {
            // 非批次物料：走一期汇总行路径（库位被 normalizeLocation 强制归零）
            changeSummaryRow(warehouseId, materialId, qty, bizType, bizId, bizNo, remark, "");
            return;
        }
        // 批次物料：(批次, 库位) 行 UPSERT（首插写 first_in_at+效期，续入不回写）
        int rows = stockMapper.increaseBatchStock(warehouseId, materialId, batch, loc, qty);
        if (rows == 0) {
            stockMapper.upsertIncreaseBatch(warehouseId, materialId, batch, loc, expiry, qty);
        }
        if (expiry != null) {
            // 行已存在且效期为空时回填（历史批次行补效期，已有值不覆盖）
            stockMapper.updateExpiryIfNull(warehouseId, materialId, batch, loc, expiry);
        }
        insertMovement(warehouseId, materialId, batch, loc, qty, bizType, bizId, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeductionLeg stockOutAtLocation(Long warehouseId, Long materialId, String batchNo,
                                           Long locationId, BigDecimal qty,
                                           InvBizType bizType, Long bizId, String bizNo, String remark) {
        validateCommon(warehouseId, materialId, qty, bizType);
        if (qty.signum() < 0) {
            throw BusinessException.ofKey("erp.inv.location-out-qty-positive");
        }
        long loc = locationId == null ? UNLOCATED : locationId;
        String batch = batchNo == null ? "" : batchNo.trim();
        log.info("[库存][出库][库位] 仓库={} 物料={} 批次={} 库位={} 数量={} 业务={} 单号={}",
                warehouseId, materialId, batch, loc, qty.stripTrailingZeros().toPlainString(),
                bizType.getCode(), bizNo);
        // 先行锁读该行再条件扣减（与策略出库同一防超卖套路），行不存在同样按库存不足回滚
        InvStock row = stockMapper.selectStockRow(warehouseId, materialId, batch, loc);
        if (row == null || row.getQty().compareTo(qty) < 0) {
            log.warn("[库存][出库][库位] 拒绝 仓库={} 物料={} 批次={} 库位={} 原因=行不存在或余量不足 请求数量={} 单号={}",
                    warehouseId, materialId, batch, loc, qty.stripTrailingZeros().toPlainString(), bizNo);
            throw BusinessException.ofKey("erp.inv.location-stock-not-enough", warehouseId, materialId, batch, loc, qty.stripTrailingZeros().toPlainString());
        }
        int updated = stockMapper.deductBatchStock(warehouseId, materialId, batch, loc, qty.negate());
        if (updated == 0) {
            throw BusinessException.ofKey("erp.inv.concurrent-deduct-conflict", warehouseId, materialId, batch, loc);
        }
        insertMovement(warehouseId, materialId, batch, loc, qty.negate(), bizType, bizId, bizNo, remark);
        return new DeductionLeg(batch, qty, loc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferLocation(Long warehouseId, Long materialId, String batchNo,
                                 Long fromLocationId, Long toLocationId, BigDecimal qty, String bizNo) {
        if (warehouseId == null || materialId == null || qty == null || qty.signum() <= 0) {
            throw BusinessException.ofKey("erp.inv.location-move-required-fields");
        }
        long from = fromLocationId == null ? UNLOCATED : fromLocationId;
        long to = toLocationId == null ? UNLOCATED : toLocationId;
        if (from == to) {
            throw BusinessException.ofKey("erp.inv.location-move-same");
        }
        boolean batchManaged = isBatchManaged(materialId);
        if (!batchManaged && (from != UNLOCATED || to != UNLOCATED)) {
            throw BusinessException.ofKey("erp.inv.non-batch-no-location-op", materialId);
        }
        String batch = batchNo == null ? "" : batchNo.trim();
        log.info("[库存][库位转移] 仓库={} 物料={} 批次={} 从库位={} 到库位={} 数量={} 单号={}",
                warehouseId, materialId, batch, from, to,
                qty.stripTrailingZeros().toPlainString(), bizNo);
        // 1) 调出行条件扣减（行锁防并发超转移）
        int deducted = stockMapper.deductBatchStock(warehouseId, materialId, batch, from, qty.negate());
        if (deducted == 0) {
            log.warn("[库存][库位转移] 拒绝 仓库={} 物料={} 批次={} 库位={} 原因=调出行余量不足 请求数量={} 单号={}",
                    warehouseId, materialId, batch, from, qty.stripTrailingZeros().toPlainString(), bizNo);
            throw BusinessException.ofKey("erp.inv.location-stock-not-enough-move", warehouseId, materialId, batch, from, qty.stripTrailingZeros().toPlainString());
        }
        // 2) 调入行加库存；效期从来源行复制（同批次效期一致，FEFO 口径不因落库位而漂移）
        InvStock fromRow = stockMapper.selectStockRow(warehouseId, materialId, batch, from);
        LocalDate expiry = fromRow != null ? fromRow.getExpiryDate() : null;
        int added = stockMapper.increaseBatchStock(warehouseId, materialId, batch, to, qty);
        if (added == 0) {
            stockMapper.upsertIncreaseBatch(warehouseId, materialId, batch, to, expiry, qty);
        }
        // ★ 口径：纯实物挪动不落 inv_movement（账面过账只在业务单据审核时发生一次），
        // 避免收发存报表按流水 SUM 时库内挪动被双计。审计依赖本方法日志 + wms_task 记录。
    }

    @Override
    public InvStock getStockRow(Long warehouseId, Long materialId, String batchNo, Long locationId) {
        return stockMapper.selectStockRow(warehouseId, materialId,
                batchNo == null ? "" : batchNo.trim(),
                locationId == null ? UNLOCATED : locationId);
    }

    // ==================== 内部工具 ====================

    /** 公共入参校验（对外 SPI，不信任任何调用方） */
    private void validateCommon(Long warehouseId, Long materialId, BigDecimal qty, InvBizType bizType) {
        if (warehouseId == null || materialId == null) {
            throw BusinessException.ofKey("erp.inv.change-required-fields");
        }
        if (qty == null || qty.signum() == 0) {
            throw BusinessException.ofKey("erp.inv.change-qty-nonzero");
        }
        if (bizType == null) {
            throw BusinessException.ofKey("erp.inv.change-biz-type-required");
        }
    }

    /**
     * batch_no='' 且 location_id=0 汇总行过账（一期逻辑原样保留：条件 UPDATE 扣减 / UPSERT 增加 + 落流水）。
     */
    private void changeSummaryRow(Long warehouseId, Long materialId, BigDecimal qty,
                                  InvBizType bizType, Long bizId, String bizNo,
                                  String remark, String batchNo) {
        if (qty.signum() > 0) {
            int rows = stockMapper.increaseStock(warehouseId, materialId, qty);
            if (rows == 0) {
                stockMapper.upsertIncrease(warehouseId, materialId, qty);
            }
        } else {
            int rows = stockMapper.deductStock(warehouseId, materialId, qty);
            if (rows == 0) {
                // warn：汇总行扣减不足（一期接口路径），与 stockOut 的拒绝日志同格式便于统一检索
                log.warn("[库存][出库] 拒绝 仓库={} 物料={} 原因=汇总行现存量不足 请求数量={} 单号={}",
                        warehouseId, materialId, qty.negate().stripTrailingZeros().toPlainString(), bizNo);
                throw BusinessException.ofKey("erp.inv.stock-not-enough", warehouseId, materialId, qty.negate().stripTrailingZeros().toPlainString());
            }
        }
        insertMovement(warehouseId, materialId, batchNo, UNLOCATED, qty, bizType, bizId, bizNo, remark);
    }

    /** 落库存流水（正入负出，与库存变更同事务；V421 起带库位） */
    private void insertMovement(Long warehouseId, Long materialId, String batchNo, long locationId,
                                BigDecimal qty, InvBizType bizType, Long bizId, String bizNo, String remark) {
        InvMovement movement = new InvMovement();
        movement.setBizType(bizType.getCode());
        movement.setBizId(bizId);
        movement.setBizNo(bizNo);
        movement.setWarehouseId(warehouseId);
        movement.setLocationId(locationId);
        movement.setMaterialId(materialId);
        movement.setBatchNo(batchNo == null ? "" : batchNo);
        movement.setQty(qty);
        movement.setRemark(remark);
        movementMapper.insert(movement);
    }

    /**
     * 锁内单行扣减（FIFO/FEFO/AVG/BATCH 拆分方案确定后逐行执行）。
     * 行已 FOR UPDATE 锁定，条件 UPDATE 命中 0 行只可能是并发异常，兜底回滚。
     */
    private void deductLockedRow(Long warehouseId, Long materialId, InvStock row,
                                 BigDecimal take, String bizNo) {
        int updated = stockMapper.deductBatchStock(warehouseId, materialId,
                row.getBatchNo(), row.getLocationId(), take.negate());
        if (updated == 0) {
            throw BusinessException.ofKey("erp.inv.concurrent-deduct-conflict", warehouseId, materialId, row.getBatchNo(), row.getLocationId());
        }
    }

    /** 拆行流水：每行一条（批次+库位），共用同一 bizNo；多行时备注追加批次标记便于人工核对 */
    private void writeLegMovements(Long warehouseId, Long materialId, List<DeductionLeg> legs,
                                   InvBizType bizType, Long bizId, String bizNo, String remark) {
        boolean multi = legs.size() > 1;
        for (DeductionLeg leg : legs) {
            String legRemark = remark == null
                    ? (multi ? "批次:" + leg.batchNo() : null)
                    : (multi ? remark + "；批次:" + leg.batchNo() : remark);
            insertMovement(warehouseId, materialId, leg.batchNo(), leg.locationId(),
                    leg.qty().negate(), bizType, bizId, bizNo, legRemark);
        }
    }

    /**
     * 入库批次号归一：批次物料必填（空白抛异常），非批次物料强制 ''。
     *
     * @return 归一后的批次号（空串 = 走汇总行）
     */
    private String normalizeBatchForIn(Long materialId, String batchNo) {
        if (isBatchManaged(materialId)) {
            if (!hasText(batchNo)) {
                throw BusinessException.ofKey("erp.inv.batch-required-on-in", materialId);
            }
            checkBatchNoLength(batchNo);
            return batchNo.trim();
        }
        return "";
    }

    /**
     * 库位归一（V421）：批次物料可落库位行；非批次物料强制 0（汇总行）——
     * 非批次物料出现 batch_no='' 多行会被 org/equipment 裸 SQL 多行命中，必须禁止。
     */
    private long normalizeLocation(Long materialId, Long locationId) {
        if (locationId == null || locationId <= 0) {
            return UNLOCATED;
        }
        if (!isBatchManaged(materialId)) {
            log.warn("[库存][入库] 物料={} 未启用批次管理，库位参数被忽略（强制汇总行）", materialId);
            return UNLOCATED;
        }
        return locationId;
    }

    /**
     * 效期归一（V422）：显式传入优先；否则按 base_material.shelf_life_days 推算（入库日+天数）。
     *
     * @return 最终效期（可 null=物料无保质期且未传）
     */
    private LocalDate resolveExpiry(Long materialId, LocalDate expiryDate) {
        if (expiryDate != null) {
            return expiryDate;
        }
        Integer shelfLifeDays = stockMapper.selectShelfLifeDays(materialId);
        if (shelfLifeDays != null && shelfLifeDays > 0) {
            return LocalDate.now().plusDays(shelfLifeDays);
        }
        return null;
    }

    /** 物料是否启用批次管理（base_material.batch_managed=1）；物料不存在抛异常 */
    private boolean isBatchManaged(Long materialId) {
        Integer managed = stockMapper.selectBatchManaged(materialId);
        if (managed == null) {
            throw BusinessException.ofKey("erp.inv.material-not-exists-no-post", materialId);
        }
        return managed == 1;
    }

    private void checkBatchNoLength(String batchNo) {
        if (batchNo != null && batchNo.trim().length() > BATCH_NO_MAX_LEN) {
            throw BusinessException.ofKey("erp.inv.batch-no-too-long", BATCH_NO_MAX_LEN);
        }
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public BigDecimal getAvailableQty(Long warehouseId, Long materialId) {
        return stockMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvStock>()
                                .eq(InvStock::getWarehouseId, warehouseId)
                                .eq(InvStock::getMaterialId, materialId))
                .stream().map(InvStock::getQty)
                .filter(q -> q != null && q.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
