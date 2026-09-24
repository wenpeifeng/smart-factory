package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.mrp.constant.MrpShopConstants;
import com.erp.mrp.dto.form.OperationForm;
import com.erp.mrp.dto.vo.OperationVO;
import com.erp.mrp.entity.MrpOperation;
import com.erp.mrp.entity.MrpRoutingStep;
import com.erp.mrp.entity.MrpWorkCenter;
import com.erp.mrp.mapper.MrpOperationMapper;
import com.erp.mrp.mapper.MrpRoutingStepMapper;
import com.erp.mrp.mapper.MrpWorkCenterMapper;
import com.erp.mrp.service.MrpOperationService;
import com.erp.mrp.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工序定义服务实现。
 * <p>
 * 关键规则：编码 OP+流水自动生成不可改；默认工作中心必须存在；
 * 删除前校验未被工艺路线步骤引用。
 */
@Service
@RequiredArgsConstructor
public class MrpOperationServiceImpl implements MrpOperationService {

    private final MrpOperationMapper operationMapper;
    private final MrpRoutingStepMapper routingStepMapper;
    private final MrpWorkCenterMapper workCenterMapper;
    private final SeqNoService seqNoService;

    @Override
    public PageResult<OperationVO> page(long pageNum, long pageSize, String keyword, String status) {
        PageResult<MrpOperation> page = PageUtils.of(operationMapper,
                Wrappers.lambdaQuery(MrpOperation.class)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(MrpOperation::getCode, keyword.trim())
                                .or().like(MrpOperation::getName, keyword.trim()))
                        .eq(status != null && !status.isBlank(), MrpOperation::getStatus, status)
                        .orderByAsc(MrpOperation::getCode),
                pageNum, pageSize);
        Map<Long, MrpWorkCenter> centers = centerMap(page.getRecords());
        return page.map(op -> toVO(op, centers));
    }

    @Override
    public List<OperationVO> listAll() {
        List<MrpOperation> list = operationMapper.selectList(
                Wrappers.lambdaQuery(MrpOperation.class).orderByAsc(MrpOperation::getCode));
        Map<Long, MrpWorkCenter> centers = centerMap(list);
        return list.stream().map(op -> toVO(op, centers)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OperationForm form) {
        validateForm(form);
        MrpOperation op = new MrpOperation();
        applyForm(op, form);
        op.setCode(seqNoService.next(MrpShopConstants.PREFIX_OPERATION));
        if (op.getStatus() == null || op.getStatus().isBlank()) {
            op.setStatus(MrpShopConstants.ENABLED);
        }
        operationMapper.insert(op);
        return op.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OperationForm form) {
        requireOperation(id);
        validateForm(form);
        MrpOperation upd = new MrpOperation();
        upd.setId(id);
        applyForm(upd, form);
        operationMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOperation(id);
        Long refs = routingStepMapper.selectCount(Wrappers.lambdaQuery(MrpRoutingStep.class)
                .eq(MrpRoutingStep::getOperationId, id));
        if (refs != null && refs > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.mrp.operation-referenced-no-delete");
        }
        operationMapper.deleteById(id);
    }

    // ==================== 私有工具 ====================

    private MrpOperation requireOperation(Long id) {
        MrpOperation op = operationMapper.selectById(id);
        if (op == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.operation-not-exists", id);
        }
        return op;
    }

    private void validateForm(OperationForm form) {
        if (form.getDefaultWorkCenterId() != null
                && workCenterMapper.selectById(form.getDefaultWorkCenterId()) == null) {
            throw BusinessException.ofKey("erp.mrp.default-workcenter-not-exists", form.getDefaultWorkCenterId());
        }
        if (form.getStatus() != null && !form.getStatus().isBlank()
                && !MrpShopConstants.ENABLED.equals(form.getStatus())
                && !MrpShopConstants.DISABLED.equals(form.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.status-invalid", form.getStatus());
        }
    }

    private void applyForm(MrpOperation op, OperationForm form) {
        op.setName(form.getName().trim());
        op.setDefaultWorkCenterId(form.getDefaultWorkCenterId());
        op.setSetupMinutes(form.getSetupMinutes() == null ? 0 : form.getSetupMinutes());
        op.setUnitMinutes(form.getUnitMinutes());
        op.setOutsourceFlag(form.getOutsourceFlag() == null ? 0 : form.getOutsourceFlag());
        op.setStatus(form.getStatus());
        op.setRemark(form.getRemark());
    }

    private Map<Long, MrpWorkCenter> centerMap(List<MrpOperation> ops) {
        List<Long> ids = ops.stream().map(MrpOperation::getDefaultWorkCenterId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workCenterMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MrpWorkCenter::getId, Function.identity()));
    }

    private OperationVO toVO(MrpOperation op, Map<Long, MrpWorkCenter> centers) {
        OperationVO vo = new OperationVO();
        vo.setId(op.getId());
        vo.setCode(op.getCode());
        vo.setName(op.getName());
        vo.setDefaultWorkCenterId(op.getDefaultWorkCenterId());
        MrpWorkCenter wc = centers.get(op.getDefaultWorkCenterId());
        if (wc != null) {
            vo.setDefaultWorkCenterCode(wc.getCode());
            vo.setDefaultWorkCenterName(wc.getName());
        }
        vo.setSetupMinutes(op.getSetupMinutes());
        vo.setUnitMinutes(op.getUnitMinutes());
        vo.setOutsourceFlag(op.getOutsourceFlag());
        vo.setStatus(op.getStatus());
        vo.setRemark(op.getRemark());
        vo.setCreateTime(op.getCreateTime());
        return vo;
    }
}
