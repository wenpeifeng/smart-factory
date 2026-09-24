package com.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.dto.form.WmsTaskCompleteForm;
import com.erp.inventory.dto.form.WmsTaskManualForm;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.WmsKpiVO;
import com.erp.inventory.dto.vo.WmsTaskVO;
import com.erp.inventory.entity.InvStock;
import com.erp.inventory.entity.WmsTask;
import com.erp.inventory.entity.WmsWave;
import com.erp.inventory.mapper.InvCheckDetailMapper;
import com.erp.inventory.mapper.InvCheckMapper;
import com.erp.inventory.mapper.WmsTaskMapper;
import com.erp.inventory.mapper.WmsWaveMapper;
import com.erp.inventory.service.StockService;
import com.erp.inventory.service.WmsTaskService;
import com.erp.inventory.support.BizNoGenerator;
import com.erp.framework.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WMS 作业任务服务实现（V421）。
 * <p>
 * 扫描器（{@link #scanOnce}）零侵入采购/销售：只读 pur_instock/sal_return/sal_outstock/
 * mrp_work_order 约定表（SQL 在 WmsTaskMapper.xml），按 (来源单, 物料, 批次) 聚合生成任务，
 * NOT EXISTS 同源未取消任务保证幂等。开关 wms.auto-task.enabled（默认 true）、
 * 回看窗口 wms.auto-task.days-back（默认 7 天，防存量单据一次性涌入）。
 * <p>
 * 完成动作的库存事务口径（与 StockService.transferLocation 同一事务）：
 * 上架=未上架行扣+库位行加（守恒、无流水）；扫描器 PICK=仅确认（账面已扣，防双扣）；
 * 手工 PICK=行级扣+OTH_OUT 流水；移库/补货=库位行间转移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsTaskServiceImpl implements WmsTaskService {

    /** 扫描器回看窗口（天）：只扫近 N 天审核的单据 */
    @Value("${wms.auto-task.days-back:7}")
    private int scanDaysBack;

    private final WmsTaskMapper taskMapper;
    private final WmsWaveMapper waveMapper;
    private final StockService stockService;
    private final BizNoGenerator bizNoGenerator;
    private final InvCheckMapper checkMapper;
    private final InvCheckDetailMapper checkDetailMapper;

    // ==================== 查询 ====================

    @Override
    public PageResult<WmsTaskVO> page(WmsTaskQuery query) {
        IPage<WmsTaskVO> page = taskMapper.selectTaskPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<Map<String, Object>> board(Long warehouseId) {
        return taskMapper.selectBoard(warehouseId);
    }

    // ==================== 执行动作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id) {
        WmsTask task = requireTask(id);
        if (task.getStatus() != WmsTask.STATUS_PENDING) {
            throw BusinessException.ofKey("erp.inv.task-already-taken", task.getTaskNo());
        }
        task.setStatus(WmsTask.STATUS_ASSIGNED);
        task.setAssignee(SecurityUtils.getCurrentUserId());
        taskMapper.updateById(task);
        // 波内首个任务被领取 → 波次推进为拣选中
        markWavePicking(task.getWaveId());
    }

    /** 波次状态推进：建波(0) → 拣选中(10)，波内任务开始执行时触发 */
    private void markWavePicking(Long waveId) {
        if (waveId == null) {
            return;
        }
        WmsWave wave = waveMapper.selectById(waveId);
        if (wave != null && wave.getStatus() == WmsWave.STATUS_CREATED) {
            wave.setStatus(WmsWave.STATUS_PICKING);
            waveMapper.updateById(wave);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        WmsTask task = requireTask(id);
        if (task.getStatus() != WmsTask.STATUS_ASSIGNED) {
            throw BusinessException.ofKey("erp.inv.task-status-not-startable", task.getTaskNo());
        }
        task.setStatus(WmsTask.STATUS_DOING);
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, WmsTaskCompleteForm form) {
        WmsTask task = requireTask(id);
        if (task.getStatus() != WmsTask.STATUS_ASSIGNED && task.getStatus() != WmsTask.STATUS_DOING) {
            throw BusinessException.ofKey("erp.inv.task-status-not-completable", task.getTaskNo());
        }
        BigDecimal actual = form != null && form.getActualQty() != null
                ? form.getActualQty() : task.getQty();
        if (actual.signum() <= 0 || actual.compareTo(task.getQty()) > 0) {
            throw BusinessException.ofKey("erp.inv.complete-qty-range", task.getQty().toPlainString());
        }
        Long locationOverride = form != null && form.getLocationId() != null
                ? form.getLocationId() : task.getLocationId();

        switch (task.getTaskType()) {
            case WmsTask.TYPE_PUTAWAY -> completePutaway(task, actual, locationOverride);
            case WmsTask.TYPE_PICK -> completePick(task, actual, locationOverride);
            case WmsTask.TYPE_MOVE, WmsTask.TYPE_REPLENISH -> completeMove(task, actual, locationOverride);
            default -> throw BusinessException.ofKey("erp.inv.unknown-task-type", task.getTaskType());
        }

        if (actual.compareTo(task.getQty()) < 0) {
            // 部分完成：任务留在执行中，剩余量继续（简单起见不拆子任务）
            task.setQty(task.getQty().subtract(actual));
            task.setStatus(WmsTask.STATUS_DOING);
            task.setRemark(appendRemark(task.getRemark(),
                    "部分完成 " + actual.stripTrailingZeros().toPlainString()));
        } else {
            task.setStatus(WmsTask.STATUS_DONE);
            task.setFinishTime(LocalDateTime.now());
        }
        if (locationOverride != null) {
            task.setLocationId(locationOverride);
        }
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        WmsTask task = requireTask(id);
        if (task.getStatus() == WmsTask.STATUS_DONE) {
            throw BusinessException.ofKey("erp.inv.task-done-no-cancel", task.getTaskNo());
        }
        if (task.getStatus() == WmsTask.STATUS_CANCELLED) {
            throw BusinessException.ofKey("erp.inv.task-cancelled", task.getTaskNo());
        }
        task.setStatus(WmsTask.STATUS_CANCELLED);
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createManual(WmsTaskManualForm form) {
        String type = form.getTaskType();
        if (WmsTask.TYPE_MOVE.equals(type) || WmsTask.TYPE_REPLENISH.equals(type)) {
            if (form.getFromLocationId() == null || form.getLocationId() == null) {
                throw BusinessException.ofKey("erp.inv.move-task-locations-required");
            }
            if (form.getFromLocationId().equals(form.getLocationId())) {
                throw BusinessException.ofKey("erp.inv.same-location");
            }
        } else if (WmsTask.TYPE_PICK.equals(type)) {
            if (form.getLocationId() == null) {
                throw BusinessException.ofKey("erp.inv.pick-task-source-required");
            }
        } else {
            throw BusinessException.ofKey("erp.inv.manual-task-types");
        }
        if (form.getQty() == null || form.getQty().signum() <= 0) {
            throw BusinessException.ofKey("erp.inv.task-qty-positive");
        }
        WmsTask task = new WmsTask();
        task.setTaskNo(bizNoGenerator.next("T"));
        task.setTaskType(type);
        task.setWarehouseId(form.getWarehouseId());
        task.setMaterialId(form.getMaterialId());
        task.setBatchNo(form.getBatchNo() == null ? "" : form.getBatchNo().trim());
        task.setQty(form.getQty());
        task.setLocationId(form.getLocationId());
        task.setFromLocationId(form.getFromLocationId());
        task.setSourceType("");
        task.setSourceId(0L);
        task.setStatus(WmsTask.STATUS_PENDING);
        task.setRecommendReason("手工创建");
        task.setRemark(form.getRemark());
        insertWithNoRetry(task);
        return task.getId();
    }

    // ==================== 扫描器 ====================

    @Override
    public int scanOnce() {
        int created = 0;
        try {
            for (Map<String, Object> c : taskMapper.selectPutawayCandidates(scanDaysBack)) {
                try {
                    created += createScanTask(c, WmsTask.TYPE_PUTAWAY);
                } catch (Exception e) {
                    log.warn("[WMS][扫描器] 上架任务生成失败 candidate={} err={}", c, e.getMessage());
                }
            }
            for (Map<String, Object> c : taskMapper.selectPickCandidates(scanDaysBack)) {
                try {
                    created += createScanTask(c, WmsTask.TYPE_PICK);
                } catch (Exception e) {
                    log.warn("[WMS][扫描器] 拣选任务生成失败 candidate={} err={}", c, e.getMessage());
                }
            }
            if (created > 0) {
                log.info("[WMS][扫描器] 本轮生成任务 {} 条", created);
            }
        } catch (Exception e) {
            // 扫描器异常吞掉记日志：调度线程不能死，下一轮重试
            log.error("[WMS][扫描器] 扫描异常：{}", e.getMessage(), e);
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generatePickTasksForDoc(String sourceType, Long sourceId, Long waveId) {
        // 复用拣选候选 SQL（按单据+物料聚合），过滤到指定单据
        int created = 0;
        for (Map<String, Object> c : taskMapper.selectPickCandidates(scanDaysBack)) {
            if (!sourceType.equals(str(c.get("source_type")))
                    || !sourceId.equals(l(c.get("source_id")))) {
                continue;
            }
            WmsTask task = buildScanTask(c, WmsTask.TYPE_PICK);
            task.setWaveId(waveId);
            insertWithNoRetry(task);
            created++;
        }
        if (created == 0) {
            throw BusinessException.ofKey("erp.inv.no-lines-for-pick-task", sourceId);
        }
        return created;
    }

    /** 扫描候选 → 落任务（含推荐库位/批次），同源幂等由候选 SQL 的 NOT EXISTS 保证 */
    private int createScanTask(Map<String, Object> candidate, String taskType) {
        BigDecimal qty = bd(candidate.get("qty"));
        if (qty == null || qty.signum() <= 0) {
            return 0;
        }
        WmsTask task = buildScanTask(candidate, taskType);
        insertWithNoRetry(task);
        return 1;
    }

    /**
     * 任务落库（单号撞号兜底重试）：Redis 序列与表可能因实例重启/并行环境短暂失配
     * （历史出现过两个后端实例共用一个 Redis 的窗口），撞 uk_task_no 时换号重试，
     * 避免扫描器整轮失败；重试耗尽抛出原始异常。
     */
    private void insertWithNoRetry(WmsTask task) {
        org.springframework.dao.DuplicateKeyException last = null;
        for (int i = 0; i < 5; i++) {
            try {
                taskMapper.insert(task);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                last = e;
                log.warn("[WMS][任务] 单号[{}]撞号（Redis 序列与表失配），换号重试第 {} 次",
                        task.getTaskNo(), i + 1);
                task.setTaskNo(bizNoGenerator.next("T"));
            }
        }
        throw last;
    }

    /** 组装扫描任务实体：PICK 先定来源库位行（含批次），PUTAWAY 再走推荐算法 */
    private WmsTask buildScanTask(Map<String, Object> candidate, String taskType) {
        Long warehouseId = l(candidate.get("warehouse_id"));
        Long materialId = l(candidate.get("material_id"));
        String batchNo = str(candidate.get("batch_no"));

        WmsTask task = new WmsTask();
        task.setTaskNo(bizNoGenerator.next("T"));
        task.setTaskType(taskType);
        task.setWarehouseId(warehouseId);
        task.setMaterialId(materialId);
        task.setQty(bd(candidate.get("qty")));
        task.setSourceType(str(candidate.get("source_type")));
        task.setSourceId(l(candidate.get("source_id")));
        task.setSourceNo(str(candidate.get("source_no")));
        task.setStatus(WmsTask.STATUS_PENDING);

        if (WmsTask.TYPE_PICK.equals(taskType)) {
            // 拣选来源：该物料有余量的库位行（余量最大优先）——批次随行确定
            Map<String, Object> row = taskMapper.selectPickSourceRow(warehouseId, materialId);
            if (row != null) {
                String rowBatch = str(row.get("batch_no"));
                if (batchNo.isEmpty() || batchNo.equals(rowBatch)) {
                    task.setBatchNo(rowBatch);
                    task.setLocationId(l(row.get("location_id")));
                    task.setRecommendReason("库位余量优先：" + str(row.get("location_code")));
                    return task;
                }
            }
            task.setBatchNo(batchNo);
            task.setRecommendReason(row == null ? "无库位库存（从未上架），账面拣选" : "指定批次无匹配库位行");
            return task;
        }

        // PUTAWAY：推荐目标库位（同物料现有库位 → STORAGE 空容库位）
        task.setBatchNo(batchNo);
        Map<String, Object> existing = taskMapper.selectExistingLocation(warehouseId, materialId);
        if (existing != null) {
            task.setLocationId(l(existing.get("location_id")));
            task.setRecommendReason("同物料现有库位：" + str(existing.get("location_code")));
            return task;
        }
        Map<String, Object> free = taskMapper.selectFreeStorageLocation(warehouseId);
        if (free != null) {
            task.setLocationId(l(free.get("location_id")));
            task.setRecommendReason("存储区空容库位：" + str(free.get("location_code")));
            return task;
        }
        task.setRecommendReason("无可用存储库位，请手工指派");
        return task;
    }

    // ==================== 完成动作的库存事务 ====================

    /**
     * 上架完成：批次物料把未上架行数量转移到库位行（同事务双写守恒、无流水）；
     * 非批次物料不落库位（口径见 StockServiceImpl），仅确认任务。
     */
    private void completePutaway(WmsTask task, BigDecimal actual, Long locationId) {
        if (locationId == null) {
            throw BusinessException.ofKey("erp.inv.putaway-location-required", task.getTaskNo());
        }
        InvStock unlocated = stockService.getStockRow(task.getWarehouseId(), task.getMaterialId(),
                task.getBatchNo(), 0L);
        if (unlocated == null || unlocated.getQty().signum() <= 0) {
            throw BusinessException.ofKey("erp.inv.no-putaway-lines", task.getTaskNo());
        }
        // 允许实际上架量小于任务量（部分上架），但不能超过未上架余量
        BigDecimal move = actual.min(unlocated.getQty());
        if (move.signum() <= 0) {
            throw BusinessException.ofKey("erp.inv.putaway-remainder-insufficient", task.getTaskNo());
        }
        stockService.transferLocation(task.getWarehouseId(), task.getMaterialId(), task.getBatchNo(),
                0L, locationId, move, task.getTaskNo());
        if (move.compareTo(actual) < 0) {
            log.warn("[WMS][上架] 任务{} 实际上架 {} 小于请求 {}（未上架余量约束）",
                    task.getTaskNo(), move.stripTrailingZeros().toPlainString(),
                    actual.stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 拣选完成：扫描器来源任务=仅确认（出库单审核已扣账面，防双扣）；
     * 手工任务（source_id=0）= 库位行精确扣减 + OTH_OUT 流水（账实同扣）。
     */
    private void completePick(WmsTask task, BigDecimal actual, Long locationId) {
        boolean fromSource = task.getSourceId() != null && task.getSourceId() > 0;
        if (!fromSource) {
            Long loc = locationId == null ? 0L : locationId;
            stockService.stockOutAtLocation(task.getWarehouseId(), task.getMaterialId(),
                    task.getBatchNo(), loc, actual, InvBizType.OTH_OUT,
                    task.getId(), task.getTaskNo(), "WMS手工拣选");
        }
        // 扫描器来源任务：账面已在出库单审核时扣减，这里只确认作业完成
    }

    /** 移库/补货完成：两库位行间转移（无流水；REPLENISH 语义=STORAGE→PICKING，创建时已校验） */
    private void completeMove(WmsTask task, BigDecimal actual, Long locationId) {
        Long to = locationId != null ? locationId : task.getLocationId();
        Long from = task.getFromLocationId();
        if (from == null || to == null) {
            throw BusinessException.ofKey("erp.inv.task-missing-locations", task.getTaskNo());
        }
        stockService.transferLocation(task.getWarehouseId(), task.getMaterialId(), task.getBatchNo(),
                from, to, actual, task.getTaskNo());
    }

    // ==================== KPI ====================

    @Override
    public WmsKpiVO kpi(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate begin = from != null ? from : end.minusDays(30);
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        WmsKpiVO vo = new WmsKpiVO();
        // 1) 完成任务量/平均耗时/及时率
        Map<String, Object> done = taskMapper.selectKpiDone(beginTime, endTime);
        vo.setCompletedTasks(l(done == null ? null : done.get("completed")));
        BigDecimal avg = done == null ? null : bd(done.get("avg_minutes"));
        vo.setAvgDurationMinutes(avg == null ? BigDecimal.ZERO
                : avg.setScale(1, RoundingMode.HALF_UP));
        Object onTime = done == null ? null : done.get("on_time");
        Object total = done == null ? null : done.get("completed");
        if (onTime != null && total != null && l(total) > 0) {
            vo.setOnTimeRate(BigDecimal.valueOf(l(onTime)).divide(BigDecimal.valueOf(l(total)), 4, RoundingMode.HALF_UP));
        } else {
            vo.setOnTimeRate(BigDecimal.ZERO);
        }
        // 2) 库存准确率：1 - 差异行/盘点行（窗口内完成的盘点单）
        Map<String, Object> acc = checkMapper.selectKpiAccuracy(beginTime, endTime);
        Long diffRows = acc == null ? 0L : l(acc.get("diff_rows"));
        Long checkRows = acc == null ? 0L : l(acc.get("check_rows"));
        vo.setStockAccuracy(checkRows == 0 ? null
                : BigDecimal.ONE.subtract(BigDecimal.valueOf(diffRows)
                        .divide(BigDecimal.valueOf(checkRows), 4, RoundingMode.HALF_UP)));
        // 3) 状态存量看板 + 人效
        vo.setStatusCounts(toStatusCounts(taskMapper.selectBoard(null)));
        vo.setByAssignee(taskMapper.selectKpiByAssignee(beginTime, endTime));
        return vo;
    }

    private List<WmsKpiVO.StatusCount> toStatusCounts(List<Map<String, Object>> board) {
        List<WmsKpiVO.StatusCount> list = new ArrayList<>();
        for (Map<String, Object> row : board) {
            WmsKpiVO.StatusCount sc = new WmsKpiVO.StatusCount();
            sc.setStatus((Integer) row.get("status"));
            sc.setCnt(l(row.get("cnt")));
            list.add(sc);
        }
        return list;
    }

    // ==================== 工具 ====================

    private WmsTask requireTask(Long id) {
        WmsTask task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.ofKey("erp.inv.task-not-exists", id);
        }
        return task;
    }

    private String appendRemark(String base, String add) {
        return base == null || base.isBlank() ? add : base + "；" + add;
    }

    private static Long l(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal bd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}
