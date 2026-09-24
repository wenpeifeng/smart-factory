package com.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseWorkcenter;
import com.mes.basedata.mapper.BaseWorkcenterMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.plan.dto.vo.WoStepVO;
import com.mes.plan.entity.PlanWoStep;
import com.mes.plan.mapper.PlanWoStepMapper;
import com.mes.plan.service.WoStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单工序任务服务实现。
 */
@Service
@RequiredArgsConstructor
public class WoStepServiceImpl implements WoStepService {

    /** 工序状态常量（DDL COMMENT 取值） */
    public static final String ST_PENDING = "PENDING_DISPATCH";
    public static final String ST_DISPATCHED = "DISPATCHED";
    public static final String ST_PROCESSING = "PROCESSING";
    public static final String ST_FINISHED = "FINISHED";

    private final PlanWoStepMapper woStepMapper;
    private final BaseWorkcenterMapper workcenterMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long stepId, Long assigneeId, String assigneeName) {
        PlanWoStep step = requireStep(stepId);
        if (!ST_PENDING.equals(step.getStatus())) {
            throw new BusinessException("仅待派工工序可派工，当前状态: %s", step.getStatus());
        }
        step.setStatus(ST_DISPATCHED);
        step.setAssigneeId(assigneeId);
        step.setAssigneeName(assigneeName);
        step.setDispatchTime(java.time.LocalDateTime.now());
        woStepMapper.updateById(step);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accumulateStation(Long stepId, BigDecimal goodQty, BigDecimal scrapQty,
                                  BigDecimal reworkQty, Integer workMinutes) {
        PlanWoStep step = requireStep(stepId);
        if (!ST_DISPATCHED.equals(step.getStatus()) && !ST_PROCESSING.equals(step.getStatus())) {
            throw new BusinessException("工序状态 %s 不允许过站报工（需已派工）", step.getStatus());
        }
        step.setFinishQty(nvl(step.getFinishQty()).add(nvl(goodQty)));
        step.setScrapQty(nvl(step.getScrapQty()).add(nvl(scrapQty)));
        step.setReworkQty(nvl(step.getReworkQty()).add(nvl(reworkQty)));
        if (workMinutes != null) {
            step.setActualMinutes((step.getActualMinutes() == null ? 0 : step.getActualMinutes()) + workMinutes);
        }
        // 合格+报废+返修 达到计划数量即工序完工
        BigDecimal done = step.getFinishQty().add(step.getScrapQty()).add(step.getReworkQty());
        if (step.getPlanQty() != null && done.compareTo(step.getPlanQty()) >= 0) {
            step.setStatus(ST_FINISHED);
        } else {
            step.setStatus(ST_PROCESSING);
        }
        woStepMapper.updateById(step);
    }

    @Override
    public List<WoStepVO> listByWorkorder(Long workorderId) {
        List<WoStepVO> steps = woStepMapper.selectList(Wrappers.lambdaQuery(PlanWoStep.class)
                        .eq(PlanWoStep::getWorkorderId, workorderId).orderByAsc(PlanWoStep::getSeqNo))
                .stream().map(WoStepVO::from).toList();
        fillWorkcenterName(steps);
        return steps;
    }

    @Override
    public WoStepVO getById(Long stepId) {
        WoStepVO vo = WoStepVO.from(requireStep(stepId));
        fillWorkcenterName(List.of(vo));
        return vo;
    }

    private void fillWorkcenterName(List<WoStepVO> steps) {
        List<Long> wcIds = steps.stream().map(WoStepVO::getWorkcenterId)
                .filter(java.util.Objects::nonNull).toList();
        if (wcIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = workcenterMapper.selectBatchIds(wcIds).stream()
                .collect(Collectors.toMap(BaseWorkcenter::getId, BaseWorkcenter::getName));
        steps.forEach(s -> s.setWorkcenterName(names.get(s.getWorkcenterId())));
    }

    private PlanWoStep requireStep(Long stepId) {
        PlanWoStep step = woStepMapper.selectById(stepId);
        if (step == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单工序不存在: %d".formatted(stepId));
        }
        return step;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
