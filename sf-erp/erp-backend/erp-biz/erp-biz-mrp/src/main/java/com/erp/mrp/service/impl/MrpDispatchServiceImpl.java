package com.erp.mrp.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.mrp.constant.MrpShopConstants;
import com.erp.mrp.dto.form.DispatchForm;
import com.erp.mrp.entity.MrpWoStep;
import com.erp.mrp.mapper.MrpWoStepMapper;
import com.erp.mrp.service.MrpDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 派工服务实现 —— PENDING_DISPATCH → DISPATCHED，记录派工对象与时间。
 */
@Service
@RequiredArgsConstructor
public class MrpDispatchServiceImpl implements MrpDispatchService {

    private final MrpWoStepMapper woStepMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long woStepId, DispatchForm form) {
        MrpWoStep step = requireStep(woStepId);
        if (!MrpShopConstants.STEP_PENDING_DISPATCH.equals(step.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.dispatch-status-not-allowed", step.getStatus());
        }
        MrpWoStep upd = new MrpWoStep();
        upd.setId(woStepId);
        upd.setAssignee(form.getAssignee().trim());
        upd.setDispatchTime(LocalDateTime.now());
        if (form.getPlanStartTime() != null) {
            // 派工时可微调计划开工时间（如换班/等料）
            upd.setPlanStartTime(form.getPlanStartTime());
        }
        upd.setStatus(MrpShopConstants.STEP_DISPATCHED);
        woStepMapper.updateById(upd);
    }

    private MrpWoStep requireStep(Long id) {
        MrpWoStep step = woStepMapper.selectById(id);
        if (step == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.operation-task-not-exists", id);
        }
        return step;
    }
}
