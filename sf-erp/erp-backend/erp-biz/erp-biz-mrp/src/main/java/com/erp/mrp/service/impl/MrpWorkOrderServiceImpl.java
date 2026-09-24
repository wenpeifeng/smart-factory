package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.cost.service.CostService;
import com.erp.framework.mybatis.PageUtils;
import com.erp.framework.security.SecurityUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.service.StockService;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.FinishForm;
import com.erp.mrp.dto.form.WorkOrderForm;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.WorkOrderVO;
import com.erp.mrp.entity.MrpBom;
import com.erp.mrp.entity.MrpSuggestion;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.event.WorkOrderReleasedEvent;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import com.erp.mrp.mapper.MrpWorkOrderMapper;
import com.erp.mrp.service.MrpBackflushService;
import com.erp.mrp.service.MrpBomService;
import com.erp.mrp.service.MrpWorkOrderService;
import com.erp.mrp.service.MrpWoStepService;
import com.erp.mrp.service.SeqNoService;
import com.erp.qc.dto.vo.InspectPendingVO;
import com.erp.qc.service.QcInspectService;
import com.erp.workflow.dto.vo.StartResultVO;
import com.erp.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生产订单服务实现。
 * <p>
 * 状态机：DRAFT → RELEASED（下达，校验 BOM 有效）→ PRODUCING（首次领料审核后）
 * → FINISHED（完工累计达标，自动）→ CLOSED（手工关闭）。
 * <p>
 * 三模块集成接线（V2101）：
 * <ul>
 *   <li>下达为三段式（审批流 mrp_work_order 类型）：SKIPPED/AGREED 直接下达，
 *       RUNNING 不执行并返回"已提交审批"（wf_started 标记区分首次/重审）；</li>
 *   <li>完工入库：QC 卡点（存在未完成检验单拒绝）+ StockService 批次入库
 *       + CostService 实时计价（PROD_IN 按当前加权价估算，工单实际成本回填属后续迭代）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrpWorkOrderServiceImpl implements MrpWorkOrderService {

    /** 审批流单据类型（dict:wf_biz_type，与 erp-workflow 白名单一致） */
    private static final String WF_BIZ_TYPE = "mrp_work_order";

    /** 审批流条件变量标准名（与节点条件表达式 amount>10000 对齐；生产订单以数量近似） */
    private static final String WF_VAR_AMOUNT = "amount";

    /** 发起结果状态：RUNNING=审批中（不执行下达） */
    private static final String WF_STATUS_RUNNING = "RUNNING";

    /** QC 挂点约定的来源单据类型（与 erp-qc QcInspect.SOURCE_MRP_WORK_ORDER 一致） */
    private static final String QC_SOURCE_MRP_WORK_ORDER = "mrp_work_order";

    /** 成本模块约定的业务类型编码（与 inventory InvBizType.PROD_IN 一致） */
    private static final String COST_BIZ_PROD_IN = "PROD_IN";

    private final MrpWorkOrderMapper workOrderMapper;
    private final MrpBomService bomService;
    private final MrpIntegrationMapper integrationMapper;
    private final MrpLookupHelper lookup;
    private final SeqNoService seqNoService;
    /** 二期扩展：下达时联动生成工单工序任务（无已审核工艺路线则内部静默跳过） */
    private final MrpWoStepService woStepService;
    /** 库存唯一入口（批次感知，替代一期裸 SQL 操作 inv_stock/inv_movement） */
    private final StockService stockService;
    /** 计价唯一入口（同事务实时归集完工入库成本） */
    private final CostService costService;
    /** 质检卡点（存在未完成检验单时拒绝完工入库） */
    private final QcInspectService qcInspectService;
    /** 倒冲领料（pick_type=BACKFLUSH 的工单完工时按 BOM 自动扣料，V621） */
    private final MrpBackflushService backflushService;
    /** 审批引擎（三段式下达） */
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<WorkOrderVO> page(long pageNum, long pageSize, String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<Long> materialIds = hasKeyword ? integrationMapper.selectMaterialIds(keyword) : List.of();
        if (hasKeyword && materialIds.isEmpty()) {
            return PageResult.of(List.of(), 0, pageNum, pageSize);
        }
        var wrapper = Wrappers.lambdaQuery(MrpWorkOrder.class)
                .eq(status != null && !status.isBlank(), MrpWorkOrder::getStatus, status)
                .in(hasKeyword, MrpWorkOrder::getMaterialId, materialIds)
                .orderByDesc(MrpWorkOrder::getId);
        PageResult<MrpWorkOrder> page = PageUtils.of(workOrderMapper, wrapper, pageNum, pageSize);
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                page.getRecords().stream().map(MrpWorkOrder::getMaterialId).toList());
        return page.map(wo -> toVO(wo, materials));
    }

    @Override
    public WorkOrderVO detail(Long id) {
        return toVO(requireOrder(id), lookup.materialMap(null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkOrderForm form) {
        MrpBom bom = requireMaterialActiveBom(form.getMaterialId());
        MrpWorkOrder wo = new MrpWorkOrder();
        applyForm(wo, form);
        wo.setOrderNo(seqNoService.next(MrpConstants.PREFIX_WORK_ORDER));
        wo.setBomId(bom.getId());
        wo.setBomVersion(bom.getVersion());
        wo.setStatus(MrpConstants.WO_DRAFT);
        wo.setFinishQty(BigDecimal.ZERO);
        wo.setBadQty(BigDecimal.ZERO);
        wo.setSourceType(MrpConstants.WO_SOURCE_MANUAL);
        workOrderMapper.insert(wo);
        return wo.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WorkOrderForm form) {
        MrpWorkOrder wo = requireOrder(id);
        requireStatus(wo, MrpConstants.WO_DRAFT, "仅草稿状态的生产订单允许修改");
        MrpBom bom = requireMaterialActiveBom(form.getMaterialId());
        MrpWorkOrder upd = new MrpWorkOrder();
        upd.setId(id);
        applyForm(upd, form);
        upd.setBomId(bom.getId());
        upd.setBomVersion(bom.getVersion());
        workOrderMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MrpWorkOrder wo = requireOrder(id);
        requireStatus(wo, MrpConstants.WO_DRAFT, "仅草稿状态的生产订单允许删除");
        workOrderMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String release(Long id) {
        MrpWorkOrder wo = requireOrder(id);
        requireStatus(wo, MrpConstants.WO_DRAFT, "仅草稿状态的生产订单允许下达");
        // 审计日志：下达含三段式审批（可能仅提交不生效），结果差异必须在日志可辨
        log.info("[生产订单][下达] 开始 订单={} 物料id={} 数量={} 操作人={}",
                wo.getOrderNo(), wo.getMaterialId(),
                wo.getQty() == null ? "0" : wo.getQty().stripTrailingZeros().toPlainString(),
                SecurityUtils.getCurrentUserId());

        // 三段式审批：vars.amount 以订单数量近似（生产订单无金额字段，条件阈值按数量表达）
        Map<String, Object> vars = Map.of(WF_VAR_AMOUNT,
                wo.getQty() == null ? BigDecimal.ZERO : wo.getQty());
        boolean started = wo.getWfStarted() != null && wo.getWfStarted() == 1;
        if (!started) {
            StartResultVO r = workflowService.startFlow(WF_BIZ_TYPE, id, wo.getOrderNo(), vars);
            if (WF_STATUS_RUNNING.equals(r.getStatus())) {
                markWfStarted(id, true);
                log.info("[生产订单][下达] 提交审批 订单={}", wo.getOrderNo());
                return "已提交审批";
            }
        } else if (workflowService.isApproved(WF_BIZ_TYPE, id)) {
            doRelease(wo);
            log.info("[生产订单][下达] 完成 订单={} 路径=审批通过后下达", wo.getOrderNo());
            return "审批已通过，订单下达完成";
        } else {
            StartResultVO r = workflowService.startFlow(WF_BIZ_TYPE, id, wo.getOrderNo(), vars);
            if (WF_STATUS_RUNNING.equals(r.getStatus())) {
                log.info("[生产订单][下达] 提交审批 订单={}", wo.getOrderNo());
                return "已提交审批";
            }
        }
        doRelease(wo);
        log.info("[生产订单][下达] 完成 订单={} 路径=免审直过", wo.getOrderNo());
        return "下达成功";
    }

    /** 原下达逻辑：校验/刷新有效 BOM -> RELEASED -> 生成工单工序任务（二期扩展） */
    private void doRelease(MrpWorkOrder wo) {
        // 下达前校验 BOM 仍为有效（可能已被停用/删除）
        MrpBom bom = bomService.findLatestActive(wo.getMaterialId());
        if (bom == null || !bom.getId().equals(wo.getBomId())) {
            MrpBom current = bom == null ? null : bom;
            if (current == null) {
                throw BusinessException.ofKey("erp.mrp.release-no-bom");
            }
            // 有有效 BOM 但不是订单快照版本：允许下达但刷新到最新有效版本
            wo.setBomId(current.getId());
            wo.setBomVersion(current.getVersion());
        }
        wo.setStatus(MrpConstants.WO_RELEASED);
        workOrderMapper.updateById(wo);
        // 二期扩展：成品有已审核工艺路线时按工序序列顺排生成工单工序任务（幂等）
        woStepService.generateForWorkOrder(wo);
        // ERP-MES 集成（V1231）：下达完成后发事件，integration 模块 AFTER_COMMIT 下推 MES（失败走 outbox 重试）
        eventPublisher.publishEvent(new WorkOrderReleasedEvent(wo.getId()));
    }

    /** 置审批流发起标记（三段式第二/三段的判定依据） */
    private void markWfStarted(Long id, boolean started) {
        MrpWorkOrder upd = new MrpWorkOrder();
        upd.setId(id);
        upd.setWfStarted(started ? 1 : 0);
        workOrderMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id, FinishForm form) {
        MrpWorkOrder wo = requireOrder(id);
        if (!MrpConstants.WO_RELEASED.equals(wo.getStatus())
                && !MrpConstants.WO_PRODUCING.equals(wo.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.finish-in-status-not-allowed", wo.getStatus());
        }
        // 审计日志：完工入库写库存+计价，良品/不良拆分是质量追溯依据
        log.info("[生产订单][完工入库] 开始 订单={} 物料id={} 良品={} 不良={} 仓库={} 批次={} 操作人={}",
                wo.getOrderNo(), wo.getMaterialId(),
                nvl(form.getGoodQty()).stripTrailingZeros().toPlainString(),
                nvl(form.getBadQty()).stripTrailingZeros().toPlainString(),
                form.getWarehouseId(), form.getBatchNo(), SecurityUtils.getCurrentUserId());
        // QC 卡点：工单存在未完成（草稿）检验单时拒绝完工；无检验单放行（免检直过）
        InspectPendingVO pending = qcInspectService.pendingOfSource(QC_SOURCE_MRP_WORK_ORDER, id);
        if (pending.isPending()) {
            String nos = pending.getPendingList().stream()
                    .map(p -> p.getInspectNo())
                    .collect(Collectors.joining("、"));
            // warn：质检卡点拒绝属过程异常，需一眼可辨
            log.warn("[生产订单][完工入库] 拒绝 订单={} 原因=存在未完成检验单({})", wo.getOrderNo(), nos);
            throw BusinessException.ofKey("erp.mrp.unfinished-inspection-no-finish", nos);
        }
        BigDecimal good = nvl(form.getGoodQty());
        BigDecimal bad = nvl(form.getBadQty());
        if (good.add(bad).signum() <= 0) {
            throw BusinessException.ofKey("erp.mrp.good-bad-not-both-zero");
        }
        if (good.signum() > 0 && form.getWarehouseId() == null) {
            throw BusinessException.ofKey("erp.mrp.finish-in-warehouse-required");
        }
        BigDecimal finishedTotal = wo.getFinishQty().add(wo.getBadQty()).add(good).add(bad);
        if (finishedTotal.compareTo(wo.getQty()) > 0) {
            // warn：完工累计超订单数量被拒，多报产/重复报产的排查入口
            log.warn("[生产订单][完工入库] 拒绝 订单={} 原因=完工累计{}超过订单数量{}",
                    wo.getOrderNo(), finishedTotal.stripTrailingZeros().toPlainString(),
                    wo.getQty().stripTrailingZeros().toPlainString());
            throw BusinessException.ofKey("erp.mrp.finish-over-order-qty", finishedTotal.stripTrailingZeros().toPlainString(), wo.getQty().stripTrailingZeros().toPlainString());
        }
        // 良品入库：StockService 批次入库（表单批次号透传，空且非批次物料落汇总行）
        //    + CostService 实时计价（PROD_IN 按当前移动加权价估算入账）
        if (good.signum() > 0) {
            stockService.stockIn(form.getWarehouseId(), wo.getMaterialId(), form.getBatchNo(),
                    good, InvBizType.PROD_IN, wo.getId(), wo.getOrderNo(),
                    "生产完工入库 " + wo.getOrderNo());
            costService.onStockIn(wo.getMaterialId(), form.getWarehouseId(), COST_BIZ_PROD_IN,
                    wo.getOrderNo(), wo.getId(), good, null, LocalDate.now());
        }

        // 倒冲领料（V621）：pick_type=BACKFLUSH 的工单按 BOM×本次良品自动扣料
        // （主料优先、替代料按优先级补足，经 StockService FIFO/PROD_USE，不足整体回滚）。
        // 扣料仓库兜底链：表单覆盖 → 工单倒冲仓 → 入库仓库。
        if (MrpConstants.PICK_BACKFLUSH.equals(wo.getPickType()) && good.signum() > 0) {
            Long pickWh = form.getBackflushWarehouseId() != null ? form.getBackflushWarehouseId()
                    : (wo.getBackflushWarehouseId() != null ? wo.getBackflushWarehouseId()
                    : form.getWarehouseId());
            backflushService.backflush(wo, good, pickWh);
        }

        MrpWorkOrder upd = new MrpWorkOrder();
        upd.setId(id);
        upd.setFinishQty(wo.getFinishQty().add(good));
        upd.setBadQty(wo.getBadQty().add(bad));
        // 良品+不良累计达到订单数量 → 已完工；否则进入/保持生产中
        upd.setStatus(finishedTotal.compareTo(wo.getQty()) >= 0
                ? MrpConstants.WO_FINISHED : MrpConstants.WO_PRODUCING);
        workOrderMapper.updateById(upd);
        log.info("[生产订单][完工入库] 完成 订单={} 本次良品={} 不良={} 累计完工={} 单据状态={}",
                wo.getOrderNo(), good.stripTrailingZeros().toPlainString(),
                bad.stripTrailingZeros().toPlainString(),
                wo.getFinishQty().add(good).stripTrailingZeros().toPlainString(), upd.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        MrpWorkOrder wo = requireOrder(id);
        requireStatus(wo, MrpConstants.WO_FINISHED, "仅已完工的订单允许关闭");
        MrpWorkOrder upd = new MrpWorkOrder();
        upd.setId(id);
        upd.setStatus(MrpConstants.WO_CLOSED);
        workOrderMapper.updateById(upd);
        // 审计日志：关闭是不可逆终态（操作人见 MDC userId）
        log.info("[生产订单][关闭] 订单={} 订单数量={} 累计完工={} 累计不良={}",
                wo.getOrderNo(), wo.getQty().stripTrailingZeros().toPlainString(),
                wo.getFinishQty().stripTrailingZeros().toPlainString(),
                wo.getBadQty().stripTrailingZeros().toPlainString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromSuggestion(MrpSuggestion suggestion, Long warehouseId) {
        if (!MrpConstants.PLAN_PRODUCE.equals(suggestion.getPlanType())) {
            throw BusinessException.ofKey("erp.mrp.only-produce-to-production");
        }
        if (!MrpConstants.SUGGESTION_PENDING.equals(suggestion.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.suggestion-converted");
        }
        MrpBom bom = requireMaterialActiveBom(suggestion.getMaterialId());
        LocalDate start = suggestion.getSuggestDate();
        MrpWorkOrder wo = new MrpWorkOrder();
        wo.setOrderNo(seqNoService.next(MrpConstants.PREFIX_WORK_ORDER));
        wo.setMaterialId(suggestion.getMaterialId());
        wo.setQty(suggestion.getQty());
        wo.setBomId(bom.getId());
        wo.setBomVersion(bom.getVersion());
        wo.setWarehouseId(warehouseId);
        wo.setPlanStartDate(start);
        wo.setPlanEndDate(start == null ? null : start.plusDays(MrpConstants.LEAD_TIME_DAYS));
        wo.setStatus(MrpConstants.WO_DRAFT);
        wo.setFinishQty(BigDecimal.ZERO);
        wo.setBadQty(BigDecimal.ZERO);
        wo.setSourceType(MrpConstants.WO_SOURCE_SUGGESTION);
        wo.setSourceId(suggestion.getId());
        workOrderMapper.insert(wo);
        return wo.getId();
    }

    // ==================== 私有工具 ====================

    private MrpWorkOrder requireOrder(Long id) {
        MrpWorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.production-order-not-exists", id);
        }
        return wo;
    }

    private void requireStatus(MrpWorkOrder wo, String expected, String message) {
        if (!expected.equals(wo.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.status-not-allowed", message, wo.getStatus());
        }
    }

    /** 成品物料存在性 + 必须有有效 BOM */
    private MrpBom requireMaterialActiveBom(Long materialId) {
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(List.of(materialId));
        if (materials.isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.finished-material-not-exists", materialId);
        }
        MrpBom bom = bomService.findLatestActive(materialId);
        if (bom == null) {
            throw BusinessException.ofKey("erp.mrp.no-valid-bom", materials.get(materialId).getCode() + "/" + materials.get(materialId).getName());
        }
        return bom;
    }

    private void applyForm(MrpWorkOrder wo, WorkOrderForm form) {
        wo.setMaterialId(form.getMaterialId());
        wo.setQty(form.getQty());
        wo.setWarehouseId(form.getWarehouseId());
        wo.setPickType(form.getPickType() == null || form.getPickType().isBlank()
                ? MrpConstants.PICK_DIRECT : form.getPickType().trim());
        wo.setBackflushWarehouseId(form.getBackflushWarehouseId());
        wo.setPlanStartDate(form.getPlanStartDate());
        wo.setPlanEndDate(form.getPlanEndDate());
        wo.setRemark(form.getRemark());
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private WorkOrderVO toVO(MrpWorkOrder wo, Map<Long, MaterialBriefVO> materials) {
        Map<Long, MaterialBriefVO> mat = materials.isEmpty()
                ? lookup.materialMap(Set.of(wo.getMaterialId())) : materials;
        WorkOrderVO vo = new WorkOrderVO();
        vo.setId(wo.getId());
        vo.setOrderNo(wo.getOrderNo());
        vo.setMaterialId(wo.getMaterialId());
        MaterialBriefVO m = mat.get(wo.getMaterialId());
        if (m != null) {
            vo.setMaterialCode(m.getCode());
            vo.setMaterialName(m.getName());
            vo.setUnit(m.getUnit());
        }
        vo.setQty(wo.getQty());
        vo.setBomId(wo.getBomId());
        vo.setBomVersion(wo.getBomVersion());
        vo.setWarehouseId(wo.getWarehouseId());
        vo.setPickType(wo.getPickType());
        vo.setBackflushWarehouseId(wo.getBackflushWarehouseId());
        vo.setPlanStartDate(wo.getPlanStartDate());
        vo.setPlanEndDate(wo.getPlanEndDate());
        vo.setStatus(wo.getStatus());
        vo.setFinishQty(wo.getFinishQty());
        vo.setBadQty(wo.getBadQty());
        vo.setSourceType(wo.getSourceType());
        vo.setSourceId(wo.getSourceId());
        vo.setRemark(wo.getRemark());
        vo.setCreateTime(wo.getCreateTime());
        return vo;
    }
}
