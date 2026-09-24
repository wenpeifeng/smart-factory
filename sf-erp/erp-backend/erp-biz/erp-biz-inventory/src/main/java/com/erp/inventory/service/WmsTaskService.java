package com.erp.inventory.service;

import com.erp.inventory.dto.form.WmsTaskCompleteForm;
import com.erp.inventory.dto.form.WmsTaskManualForm;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.WmsKpiVO;
import com.erp.inventory.dto.vo.WmsTaskVO;
import com.erp.common.result.PageResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * WMS 作业任务 Service（V421）—— 任务全生命周期：生成（扫描器/波次/手工）→ 领取 → 执行 → 完成/取消。
 * <p>
 * 扫描器模式（零侵入采购/销售）：{@code scanOnce()} 每 30s 由调度器触发
 * （开关 wms.auto-task.enabled，默认 true），只读约定表生成 PUTAWAY/PICK 任务。
 */
public interface WmsTaskService {

    /**
     * 任务分页（类型/状态/仓库/执行人/关键词筛选）。
     */
    PageResult<WmsTaskVO> page(WmsTaskQuery query);

    /**
     * 任务看板：按 (task_type, status) 分组的未完成任务计数。
     *
     * @param warehouseId 仓库 ID（null=全仓）
     * @return [{task_type=.., status=.., cnt=..}]
     */
    List<Map<String, Object>> board(Long warehouseId);

    /**
     * 领取任务：待分配(0) → 待执行(10)，执行人=当前登录用户。
     *
     * @param id 任务 ID
     */
    void claim(Long id);

    /**
     * 开始任务：待执行(10) → 执行中(20)（移动端可选步骤，完成允许从 10/20 直达）。
     */
    void start(Long id);

    /**
     * 完成任务（核心库存事务，见 WmsTask 实体口径注释）：
     * <ul>
     *   <li>PUTAWAY：批次物料 = transferLocation(未上架行→库位行)，同事务双写守恒；
     *       非批次物料 = 账面不动；</li>
     *   <li>PICK（有来源）：账面已在出库单审核时扣减，完成仅确认；</li>
     *   <li>PICK（手工）：stockOutAtLocation 精确行扣 + OTH_OUT 流水；</li>
     *   <li>MOVE/REPLENISH：transferLocation 两库位行转移。</li>
     * </ul>
     * 部分完成：actualQty &lt; 任务数量时任务留在执行中，剩余量=任务数量-actualQty。
     *
     * @param id   任务 ID
     * @param form 完成表单（actualQty/locationId 可空）
     */
    void complete(Long id, WmsTaskCompleteForm form);

    /**
     * 取消任务（待分配/待执行/执行中可取消；波次内任务取消后波次仍可完成）。
     */
    void cancel(Long id);

    /**
     * 手工创建任务（MOVE/REPLENISH/PICK；PUTAWAY 不开放手工）。
     *
     * @return 任务 ID
     */
    Long createManual(WmsTaskManualForm form);

    /**
     * 扫描器执行一轮：扫已审核未生成任务的入库单→PUTAWAY、出库单→PICK。
     * 幂等（NOT EXISTS 同源任务）；异常吞掉记日志，不中断调度线程。
     *
     * @return 本轮新生成任务数
     */
    int scanOnce();

    /**
     * 为单张出库单生成拣选任务（建波复用；不入波时 waveId 传 null）。
     * 任务重复防护：同 (source, material) 已有未取消任务则跳过。
     *
     * @param sourceType SAL_OUT / OTH_OUT
     * @param waveId     波次 ID（null=独立任务）
     * @return 生成的任务数
     */
    int generatePickTasksForDoc(String sourceType, Long sourceId, Long waveId);

    /**
     * KPI 统计（任务完成量/人效/及时率/库存准确率 + 状态存量看板）。
     *
     * @param from 统计起始日（null=30 天前）
     * @param to   统计截止日（null=今天）
     */
    WmsKpiVO kpi(LocalDate from, LocalDate to);
}
