package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.mrp.constant.MrpShopConstants;
import com.erp.mrp.dto.form.WorkCenterForm;
import com.erp.mrp.dto.vo.WorkCenterVO;
import com.erp.mrp.entity.MrpOperation;
import com.erp.mrp.entity.MrpRoutingStep;
import com.erp.mrp.entity.MrpWoStep;
import com.erp.mrp.entity.MrpWorkCenter;
import com.erp.mrp.mapper.MrpOperationMapper;
import com.erp.mrp.mapper.MrpRoutingStepMapper;
import com.erp.mrp.mapper.MrpWorkCenterMapper;
import com.erp.mrp.mapper.MrpWoStepMapper;
import com.erp.mrp.service.MrpWorkCenterService;
import com.erp.mrp.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作中心服务实现。
 * <p>
 * 关键规则：编码 WC+流水自动生成不可改；删除前校验未被
 * 工序默认值 / 工艺路线步骤 / 工单工序任务引用。
 */
@Service
@RequiredArgsConstructor
public class MrpWorkCenterServiceImpl implements MrpWorkCenterService {

    private final MrpWorkCenterMapper workCenterMapper;
    private final MrpOperationMapper operationMapper;
    private final MrpRoutingStepMapper routingStepMapper;
    private final MrpWoStepMapper woStepMapper;
    private final SeqNoService seqNoService;

    @Override
    public PageResult<WorkCenterVO> page(long pageNum, long pageSize, String keyword, String status) {
        PageResult<MrpWorkCenter> page = PageUtils.of(workCenterMapper,
                Wrappers.lambdaQuery(MrpWorkCenter.class)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(MrpWorkCenter::getCode, keyword.trim())
                                .or().like(MrpWorkCenter::getName, keyword.trim())
                                .or().like(MrpWorkCenter::getWorkshop, keyword.trim()))
                        .eq(status != null && !status.isBlank(), MrpWorkCenter::getStatus, status)
                        .orderByAsc(MrpWorkCenter::getCode),
                pageNum, pageSize);
        return page.map(this::toVO);
    }

    @Override
    public List<WorkCenterVO> listAll() {
        return workCenterMapper.selectList(Wrappers.lambdaQuery(MrpWorkCenter.class)
                        .orderByAsc(MrpWorkCenter::getCode))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkCenterForm form) {
        MrpWorkCenter wc = new MrpWorkCenter();
        applyForm(wc, form);
        wc.setCode(seqNoService.next(MrpShopConstants.PREFIX_WORK_CENTER));
        if (wc.getStatus() == null || wc.getStatus().isBlank()) {
            wc.setStatus(MrpShopConstants.ENABLED);
        }
        workCenterMapper.insert(wc);
        return wc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WorkCenterForm form) {
        requireWorkCenter(id);
        MrpWorkCenter upd = new MrpWorkCenter();
        upd.setId(id);
        applyForm(upd, form);
        workCenterMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireWorkCenter(id);
        requireNotReferenced(id);
        workCenterMapper.deleteById(id);
    }

    // ==================== 私有工具 ====================

    private MrpWorkCenter requireWorkCenter(Long id) {
        MrpWorkCenter wc = workCenterMapper.selectById(id);
        if (wc == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.workcenter-not-exists", id);
        }
        return wc;
    }

    /** 删除防引用：工序默认值 / 工艺路线步骤 / 工单工序任务 */
    private void requireNotReferenced(Long id) {
        Long ops = operationMapper.selectCount(Wrappers.lambdaQuery(MrpOperation.class)
                .eq(MrpOperation::getDefaultWorkCenterId, id));
        if (ops != null && ops > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.mrp.workcenter-referenced-default");
        }
        Long routingSteps = routingStepMapper.selectCount(Wrappers.lambdaQuery(MrpRoutingStep.class)
                .eq(MrpRoutingStep::getWorkCenterId, id));
        if (routingSteps != null && routingSteps > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.mrp.workcenter-referenced-routing");
        }
        Long woSteps = woStepMapper.selectCount(Wrappers.lambdaQuery(MrpWoStep.class)
                .eq(MrpWoStep::getWorkCenterId, id));
        if (woSteps != null && woSteps > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.mrp.workcenter-referenced-task");
        }
    }

    private void applyForm(MrpWorkCenter wc, WorkCenterForm form) {
        wc.setName(form.getName().trim());
        wc.setWorkshop(form.getWorkshop());
        wc.setCapacityHoursPerDay(form.getCapacityHoursPerDay());
        wc.setEquipmentCount(form.getEquipmentCount());
        wc.setStatus(form.getStatus());
        wc.setRemark(form.getRemark());
    }

    private WorkCenterVO toVO(MrpWorkCenter wc) {
        WorkCenterVO vo = new WorkCenterVO();
        vo.setId(wc.getId());
        vo.setCode(wc.getCode());
        vo.setName(wc.getName());
        vo.setWorkshop(wc.getWorkshop());
        vo.setCapacityHoursPerDay(wc.getCapacityHoursPerDay());
        vo.setEquipmentCount(wc.getEquipmentCount());
        vo.setStatus(wc.getStatus());
        vo.setRemark(wc.getRemark());
        vo.setCreateTime(wc.getCreateTime());
        return vo;
    }
}
