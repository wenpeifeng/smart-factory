package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.mrp.constant.MrpShopConstants;
import com.erp.mrp.dto.vo.DispatchBoardVO;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.WoStepVO;
import com.erp.mrp.entity.MrpOperation;
import com.erp.mrp.entity.MrpRouting;
import com.erp.mrp.entity.MrpRoutingStep;
import com.erp.mrp.entity.MrpWorkCenter;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.entity.MrpWoStep;
import com.erp.mrp.mapper.MrpRoutingStepMapper;
import com.erp.mrp.mapper.MrpWorkOrderMapper;
import com.erp.mrp.mapper.MrpWoStepMapper;
import com.erp.mrp.service.MrpRoutingService;
import com.erp.mrp.service.MrpWoStepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工单工序任务服务实现。
 * <p>
 * 生产订单下达联动：成品有已审核(ACTIVE)工艺路线时按工序序列顺排生成任务，
 * 无路线静默跳过（一期流程不受影响）；生成过则幂等跳过（防重复下达）。
 * 顺排规则：首道工序计划开始 = 订单计划开工日(空取当天) 08:00，
 * 每道工序计划开始 = 前置工序计划结束，计划结束 = 计划开始 + 单件工时 × 订单数量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrpWoStepServiceImpl implements MrpWoStepService {

    private final MrpWoStepMapper woStepMapper;
    private final MrpRoutingStepMapper routingStepMapper;
    private final MrpWorkOrderMapper workOrderMapper;
    private final MrpRoutingService routingService;
    private final MrpShopLookupHelper shopLookup;
    private final MrpLookupHelper lookup;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateForWorkOrder(MrpWorkOrder wo) {
        if (woStepMapper.countByWorkOrderId(wo.getId()) > 0) {
            log.debug("生产订单 {} 已生成工序任务，跳过", wo.getOrderNo());
            return;
        }
        MrpRouting routing = routingService.findLatestActive(wo.getMaterialId());
        if (routing == null) {
            log.debug("生产订单 {} 成品无已审核工艺路线，跳过工序任务生成", wo.getOrderNo());
            return;
        }
        List<MrpRoutingStep> steps = routingStepMapper.selectList(
                Wrappers.lambdaQuery(MrpRoutingStep.class)
                        .eq(MrpRoutingStep::getRoutingId, routing.getId())
                        .orderByAsc(MrpRoutingStep::getSeqNo).orderByAsc(MrpRoutingStep::getId));
        if (steps.isEmpty()) {
            return;
        }
        Map<Long, MrpOperation> operations = shopLookup.operationMap(
                steps.stream().map(MrpRoutingStep::getOperationId).toList());
        // 首道工序计划开始：订单计划开工日 08:00，无计划开工日取当天
        LocalDate baseDate = wo.getPlanStartDate() != null ? wo.getPlanStartDate() : LocalDate.now();
        LocalDateTime cursor = baseDate.atTime(MrpShopConstants.DAY_SHIFT_START_HOUR, 0);
        for (MrpRoutingStep step : steps) {
            MrpOperation op = operations.get(step.getOperationId());
            BigDecimal unitMinutes = step.getUnitMinutes() != null ? step.getUnitMinutes()
                    : (op == null ? BigDecimal.ZERO : op.getUnitMinutes());
            Long workCenterId = step.getWorkCenterId() != null ? step.getWorkCenterId()
                    : (op == null ? null : op.getDefaultWorkCenterId());
            // 分钟 = 单件工时 × 订单数量，向上取整（避免小数分钟丢失尾差）
            long minutes = unitMinutes.multiply(wo.getQty())
                    .setScale(0, RoundingMode.CEILING).longValue();
            LocalDateTime end = minutes <= 0 ? cursor : cursor.plusMinutes(minutes);
            MrpWoStep woStep = new MrpWoStep();
            woStep.setWorkOrderId(wo.getId());
            woStep.setSeqNo(step.getSeqNo());
            woStep.setOperationId(step.getOperationId());
            woStep.setWorkCenterId(workCenterId);
            woStep.setUnitMinutes(unitMinutes == null ? BigDecimal.ZERO : unitMinutes);
            woStep.setPlanQty(wo.getQty());
            woStep.setPlanStartTime(cursor);
            woStep.setPlanEndTime(end);
            woStep.setStatus(MrpShopConstants.STEP_PENDING_DISPATCH);
            woStep.setFinishQty(BigDecimal.ZERO);
            woStep.setBadQty(BigDecimal.ZERO);
            woStep.setActualMinutes(0);
            woStepMapper.insert(woStep);
            cursor = end;
        }
    }

    @Override
    public DispatchBoardVO board(String status, Long workCenterId) {
        List<MrpWoStep> steps = woStepMapper.selectList(Wrappers.lambdaQuery(MrpWoStep.class)
                .eq(status != null && !status.isBlank(), MrpWoStep::getStatus, status)
                .eq(workCenterId != null, MrpWoStep::getWorkCenterId, workCenterId)
                .orderByAsc(MrpWoStep::getWorkCenterId)
                .orderByAsc(MrpWoStep::getWorkOrderId)
                .orderByAsc(MrpWoStep::getSeqNo));
        List<WoStepVO> vos = toVOs(steps);
        // 按工作中心分组（LinkedHashMap 保持按 workCenterId 升序；null 归"未分组"放最后）
        Map<Long, List<WoStepVO>> grouped = new LinkedHashMap<>();
        for (WoStepVO vo : vos) {
            grouped.computeIfAbsent(vo.getWorkCenterId() == null ? -1L : vo.getWorkCenterId(),
                    k -> new ArrayList<>()).add(vo);
        }
        Map<Long, MrpWorkCenter> centers = shopLookup.workCenterMap(grouped.keySet());
        DispatchBoardVO board = new DispatchBoardVO();
        List<DispatchBoardVO.Group> groups = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            DispatchBoardVO.Group group = new DispatchBoardVO.Group();
            Long wcId = entry.getKey().equals(-1L) ? null : entry.getKey();
            MrpWorkCenter wc = wcId == null ? null : centers.get(wcId);
            group.setWorkCenterId(wcId);
            if (wc != null) {
                group.setWorkCenterCode(wc.getCode());
                group.setWorkCenterName(wc.getName());
                group.setCapacityHoursPerDay(wc.getCapacityHoursPerDay());
            }
            group.setSteps(entry.getValue());
            group.setStepCount(entry.getValue().size());
            groups.add(group);
        }
        board.setGroups(groups);
        return board;
    }

    @Override
    public List<WoStepVO> listByWorkOrder(Long workOrderId) {
        MrpWorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.production-order-not-exists", workOrderId);
        }
        return toVOs(woStepMapper.selectList(Wrappers.lambdaQuery(MrpWoStep.class)
                .eq(MrpWoStep::getWorkOrderId, workOrderId)
                .orderByAsc(MrpWoStep::getSeqNo).orderByAsc(MrpWoStep::getId)));
    }

    // ==================== 私有工具 ====================

    /** 批量 VO 组装：订单号/成品/工序/工作中心一次查齐，避免 N+1 */
    private List<WoStepVO> toVOs(List<MrpWoStep> steps) {
        if (steps.isEmpty()) {
            return List.of();
        }
        Map<Long, MrpWorkOrder> orders = workOrderMapper.selectBatchIds(
                        steps.stream().map(MrpWoStep::getWorkOrderId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(
                        MrpWorkOrder::getId, o -> o));
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                orders.values().stream().map(MrpWorkOrder::getMaterialId).toList());
        Map<Long, MrpOperation> operations = shopLookup.operationMap(
                steps.stream().map(MrpWoStep::getOperationId).distinct().toList());
        Map<Long, MrpWorkCenter> centers = shopLookup.workCenterMap(
                steps.stream().map(MrpWoStep::getWorkCenterId).filter(Objects::nonNull).toList());
        return steps.stream().map(step -> {
            MrpWorkOrder wo = orders.get(step.getWorkOrderId());
            WoStepVO vo = new WoStepVO();
            vo.setId(step.getId());
            vo.setWorkOrderId(step.getWorkOrderId());
            if (wo != null) {
                vo.setOrderNo(wo.getOrderNo());
                MaterialBriefVO m = materials.get(wo.getMaterialId());
                if (m != null) {
                    vo.setMaterialCode(m.getCode());
                    vo.setMaterialName(m.getName());
                }
            }
            vo.setSeqNo(step.getSeqNo());
            vo.setOperationId(step.getOperationId());
            MrpOperation op = operations.get(step.getOperationId());
            if (op != null) {
                vo.setOperationCode(op.getCode());
                vo.setOperationName(op.getName());
            }
            vo.setWorkCenterId(step.getWorkCenterId());
            MrpWorkCenter wc = step.getWorkCenterId() == null
                    ? null : centers.get(step.getWorkCenterId());
            if (wc != null) {
                vo.setWorkCenterCode(wc.getCode());
                vo.setWorkCenterName(wc.getName());
            }
            vo.setUnitMinutes(step.getUnitMinutes());
            vo.setPlanQty(step.getPlanQty());
            vo.setFinishQty(step.getFinishQty());
            vo.setBadQty(step.getBadQty());
            if (step.getPlanQty() != null && step.getPlanQty().signum() > 0) {
                vo.setProgress(step.getFinishQty().multiply(BigDecimal.valueOf(100))
                        .divide(step.getPlanQty(), 1, RoundingMode.HALF_UP));
            } else {
                vo.setProgress(BigDecimal.ZERO);
            }
            vo.setPlanStartTime(step.getPlanStartTime());
            vo.setPlanEndTime(step.getPlanEndTime());
            vo.setStatus(step.getStatus());
            vo.setAssignee(step.getAssignee());
            vo.setDispatchTime(step.getDispatchTime());
            vo.setActualMinutes(step.getActualMinutes());
            vo.setQcPendingNote(step.getQcPendingNote());
            return vo;
        }).toList();
    }
}
