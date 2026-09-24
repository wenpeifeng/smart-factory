package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.mrp.constant.MrpShopConstants;
import com.erp.mrp.dto.form.RoutingCopyForm;
import com.erp.mrp.dto.form.RoutingForm;
import com.erp.mrp.dto.form.RoutingStepForm;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.RoutingStepVO;
import com.erp.mrp.dto.vo.RoutingTreeVO;
import com.erp.mrp.dto.vo.RoutingVO;
import com.erp.mrp.entity.MrpOperation;
import com.erp.mrp.entity.MrpRouting;
import com.erp.mrp.entity.MrpRoutingStep;
import com.erp.mrp.entity.MrpWorkCenter;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import com.erp.mrp.mapper.MrpOperationMapper;
import com.erp.mrp.mapper.MrpRoutingMapper;
import com.erp.mrp.mapper.MrpRoutingStepMapper;
import com.erp.mrp.mapper.MrpWorkCenterMapper;
import com.erp.mrp.service.MrpRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 工艺路线服务实现。
 * <p>
 * 关键规则：
 * <ul>
 *   <li>uk(material_id, version) 数据库唯一 + 服务层前置校验（错误信息更友好）；</li>
 *   <li>仅 DRAFT 可编辑/删除；步骤整体重建（与一期 BOM 明细同构）；</li>
 *   <li>步骤序号必须从1起且连续不重复；工序/覆盖的工作中心必须存在；</li>
 *   <li>工单工序任务在下达时已落快照（mrp_wo_step），路线删除不影响已生成任务追溯。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MrpRoutingServiceImpl implements MrpRoutingService {

    private final MrpRoutingMapper routingMapper;
    private final MrpRoutingStepMapper routingStepMapper;
    private final MrpOperationMapper operationMapper;
    private final MrpWorkCenterMapper workCenterMapper;
    private final MrpIntegrationMapper integrationMapper;
    private final MrpShopLookupHelper shopLookup;
    private final MrpLookupHelper lookup;

    @Override
    public PageResult<RoutingVO> page(long pageNum, long pageSize, String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<Long> materialIds = hasKeyword ? integrationMapper.selectMaterialIds(keyword) : List.of();
        if (hasKeyword && materialIds.isEmpty()) {
            return PageResult.of(List.of(), 0, pageNum, pageSize);
        }
        PageResult<MrpRouting> page = PageUtils.of(routingMapper,
                Wrappers.lambdaQuery(MrpRouting.class)
                        .eq(status != null && !status.isBlank(), MrpRouting::getStatus, status)
                        .in(hasKeyword, MrpRouting::getMaterialId, materialIds)
                        .orderByDesc(MrpRouting::getId),
                pageNum, pageSize);
        Map<Long, Long> stepCount = countSteps(
                page.getRecords().stream().map(MrpRouting::getId).toList());
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                page.getRecords().stream().map(MrpRouting::getMaterialId).toList());
        return page.map(r -> toVO(r, stepCount.getOrDefault(r.getId(), 0L), materials));
    }

    @Override
    public RoutingVO detail(Long id) {
        MrpRouting routing = requireRouting(id);
        RoutingVO vo = toVO(routing, null, lookup.materialMap(List.of(routing.getMaterialId())));
        vo.setSteps(stepVOs(id));
        return vo;
    }

    @Override
    public RoutingTreeVO tree(Long id) {
        MrpRouting routing = requireRouting(id);
        MaterialBriefVO material = lookup.materialMap(List.of(routing.getMaterialId()))
                .get(routing.getMaterialId());
        RoutingTreeVO vo = new RoutingTreeVO();
        vo.setRoutingId(routing.getId());
        vo.setMaterialId(routing.getMaterialId());
        if (material != null) {
            vo.setMaterialCode(material.getCode());
            vo.setMaterialName(material.getName());
        }
        vo.setVersion(routing.getVersion());
        vo.setStatus(routing.getStatus());
        for (RoutingStepVO step : stepVOs(id)) {
            RoutingTreeVO.StepNode node = new RoutingTreeVO.StepNode();
            node.setSeqNo(step.getSeqNo());
            node.setOperationCode(step.getOperationCode());
            node.setOperationName(step.getOperationName());
            node.setWorkCenterCode(step.getWorkCenterCode());
            node.setWorkCenterName(step.getWorkCenterName());
            node.setEffectiveUnitMinutes(step.getEffectiveUnitMinutes());
            node.setReportFlag(step.getReportFlag());
            vo.getSteps().add(node);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoutingForm form) {
        validateForm(null, form);
        MrpRouting routing = new MrpRouting();
        routing.setMaterialId(form.getMaterialId());
        routing.setVersion(form.getVersion().trim());
        routing.setStatus(MrpShopConstants.ROUTING_DRAFT);
        routing.setRemark(form.getRemark());
        routingMapper.insert(routing);
        insertSteps(routing.getId(), form);
        return routing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoutingForm form) {
        MrpRouting routing = requireRouting(id);
        if (!MrpShopConstants.ROUTING_DRAFT.equals(routing.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.routing-only-draft-modify");
        }
        validateForm(id, form);
        MrpRouting upd = new MrpRouting();
        upd.setId(id);
        upd.setMaterialId(form.getMaterialId());
        upd.setVersion(form.getVersion().trim());
        upd.setRemark(form.getRemark());
        routingMapper.updateById(upd);
        // 步骤整体重建（旧步骤逻辑删除）
        routingStepMapper.delete(Wrappers.lambdaQuery(MrpRoutingStep.class)
                .eq(MrpRoutingStep::getRoutingId, id));
        insertSteps(id, form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MrpRouting routing = requireRouting(id);
        if (MrpShopConstants.ROUTING_ACTIVE.equals(routing.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.routing-audited-no-delete");
        }
        routingStepMapper.delete(Wrappers.lambdaQuery(MrpRoutingStep.class)
                .eq(MrpRoutingStep::getRoutingId, id));
        routingMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id, RoutingCopyForm form) {
        MrpRouting source = requireRouting(id);
        String newVersion = form.getNewVersion().trim();
        if (versionExists(source.getMaterialId(), newVersion, null)) {
            throw BusinessException.ofKey("erp.mrp.routing-version-exists", newVersion);
        }
        MrpRouting target = new MrpRouting();
        target.setMaterialId(source.getMaterialId());
        target.setVersion(newVersion);
        target.setStatus(MrpShopConstants.ROUTING_DRAFT);
        target.setRemark(source.getRemark());
        routingMapper.insert(target);
        for (MrpRoutingStep step : listSteps(id)) {
            MrpRoutingStep copy = new MrpRoutingStep();
            copy.setRoutingId(target.getId());
            copy.setSeqNo(step.getSeqNo());
            copy.setOperationId(step.getOperationId());
            copy.setWorkCenterId(step.getWorkCenterId());
            copy.setUnitMinutes(step.getUnitMinutes());
            copy.setReportFlag(step.getReportFlag());
            copy.setRemark(step.getRemark());
            routingStepMapper.insert(copy);
        }
        return target.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        MrpRouting routing = requireRouting(id);
        if (!MrpShopConstants.ROUTING_DRAFT.equals(routing.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.routing-only-draft-audit");
        }
        if (listSteps(id).isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.routing-no-steps-no-audit");
        }
        MrpRouting upd = new MrpRouting();
        upd.setId(id);
        upd.setStatus(MrpShopConstants.ROUTING_ACTIVE);
        routingMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        MrpRouting routing = requireRouting(id);
        if (!MrpShopConstants.ROUTING_ACTIVE.equals(routing.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.routing-only-audited-disable");
        }
        MrpRouting upd = new MrpRouting();
        upd.setId(id);
        upd.setStatus(MrpShopConstants.ROUTING_DISABLED);
        routingMapper.updateById(upd);
    }

    @Override
    public MrpRouting findLatestActive(Long materialId) {
        return routingMapper.selectOne(Wrappers.lambdaQuery(MrpRouting.class)
                .eq(MrpRouting::getMaterialId, materialId)
                .eq(MrpRouting::getStatus, MrpShopConstants.ROUTING_ACTIVE)
                .orderByDesc(MrpRouting::getId)
                .last("LIMIT 1"));
    }

    // ==================== 私有工具 ====================

    private MrpRouting requireRouting(Long id) {
        MrpRouting routing = routingMapper.selectById(id);
        if (routing == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.routing-not-exists", id);
        }
        return routing;
    }

    private List<MrpRoutingStep> listSteps(Long routingId) {
        return routingStepMapper.selectList(Wrappers.lambdaQuery(MrpRoutingStep.class)
                .eq(MrpRoutingStep::getRoutingId, routingId)
                .orderByAsc(MrpRoutingStep::getSeqNo).orderByAsc(MrpRoutingStep::getId));
    }

    /** 步骤 VO（含工序/工作中心回显与生效单件工时） */
    private List<RoutingStepVO> stepVOs(Long routingId) {
        List<MrpRoutingStep> steps = listSteps(routingId);
        if (steps.isEmpty()) {
            return List.of();
        }
        Map<Long, MrpOperation> operations = shopLookup.operationMap(
                steps.stream().map(MrpRoutingStep::getOperationId).toList());
        Map<Long, MrpWorkCenter> centers = shopLookup.workCenterMap(
                effectiveWorkCenterIds(steps, operations));
        List<RoutingStepVO> result = new ArrayList<>(steps.size());
        for (MrpRoutingStep step : steps) {
            MrpOperation op = operations.get(step.getOperationId());
            Long effectiveWc = effectiveWorkCenterId(step, op);
            MrpWorkCenter wc = centers.get(effectiveWc);
            RoutingStepVO vo = new RoutingStepVO();
            vo.setId(step.getId());
            vo.setSeqNo(step.getSeqNo());
            vo.setOperationId(step.getOperationId());
            if (op != null) {
                vo.setOperationCode(op.getCode());
                vo.setOperationName(op.getName());
                vo.setOutsourceFlag(op.getOutsourceFlag());
            }
            vo.setWorkCenterId(effectiveWc);
            if (wc != null) {
                vo.setWorkCenterCode(wc.getCode());
                vo.setWorkCenterName(wc.getName());
            }
            vo.setUnitMinutes(step.getUnitMinutes());
            vo.setEffectiveUnitMinutes(step.getUnitMinutes() != null ? step.getUnitMinutes()
                    : (op == null ? BigDecimal.ZERO : op.getUnitMinutes()));
            vo.setReportFlag(step.getReportFlag());
            vo.setRemark(step.getRemark());
            result.add(vo);
        }
        return result;
    }

    /** 步骤生效工作中心：步骤覆盖值 ?: 工序默认值 */
    private Long effectiveWorkCenterId(MrpRoutingStep step, MrpOperation op) {
        if (step.getWorkCenterId() != null) {
            return step.getWorkCenterId();
        }
        return op == null ? null : op.getDefaultWorkCenterId();
    }

    private Set<Long> effectiveWorkCenterIds(List<MrpRoutingStep> steps,
                                             Map<Long, MrpOperation> operations) {
        Set<Long> ids = new HashSet<>();
        for (MrpRoutingStep step : steps) {
            Long id = effectiveWorkCenterId(step, operations.get(step.getOperationId()));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<Long, Long> countSteps(List<Long> routingIds) {
        if (routingIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new TreeMap<>();
        for (MrpRoutingStep step : routingStepMapper.selectList(
                Wrappers.lambdaQuery(MrpRoutingStep.class).in(MrpRoutingStep::getRoutingId, routingIds))) {
            counts.merge(step.getRoutingId(), 1L, Long::sum);
        }
        return counts;
    }

    private void insertSteps(Long routingId, RoutingForm form) {
        for (RoutingStepForm stepForm : form.getSteps()) {
            MrpRoutingStep step = new MrpRoutingStep();
            step.setRoutingId(routingId);
            step.setSeqNo(stepForm.getSeqNo());
            step.setOperationId(stepForm.getOperationId());
            step.setWorkCenterId(stepForm.getWorkCenterId());
            step.setUnitMinutes(stepForm.getUnitMinutes());
            step.setReportFlag(stepForm.getReportFlag() == null ? 1 : stepForm.getReportFlag());
            step.setRemark(stepForm.getRemark());
            routingStepMapper.insert(step);
        }
    }

    /** 表单校验：物料/工序/工作中心存在、序号从1连续不重复、版本唯一 */
    private void validateForm(Long excludeId, RoutingForm form) {
        if (lookup.materialMap(List.of(form.getMaterialId())).isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.finished-material-not-exists", form.getMaterialId());
        }
        Map<Long, MrpOperation> operations = shopLookup.operationMap(
                form.getSteps().stream().map(RoutingStepForm::getOperationId).toList());
        Set<Long> wcIds = new HashSet<>();
        for (RoutingStepForm step : form.getSteps()) {
            if (!operations.containsKey(step.getOperationId())) {
                throw BusinessException.ofKey("erp.mrp.operation-not-exists-id", step.getOperationId());
            }
            if (step.getWorkCenterId() != null) {
                wcIds.add(step.getWorkCenterId());
            }
        }
        Map<Long, MrpWorkCenter> centers = shopLookup.workCenterMap(wcIds);
        for (RoutingStepForm step : form.getSteps()) {
            if (step.getWorkCenterId() != null && !centers.containsKey(step.getWorkCenterId())) {
                throw BusinessException.ofKey("erp.mrp.step-workcenter-not-exists", step.getSeqNo(), step.getWorkCenterId());
            }
        }
        Set<Integer> seqSet = new HashSet<>();
        for (RoutingStepForm step : form.getSteps()) {
            if (!seqSet.add(step.getSeqNo())) {
                throw BusinessException.ofKey("erp.mrp.step-no-duplicate", step.getSeqNo());
            }
        }
        for (int i = 1; i <= form.getSteps().size(); i++) {
            if (!seqSet.contains(i)) {
                throw BusinessException.ofKey("erp.mrp.step-no-continuous", i);
            }
        }
        if (versionExists(form.getMaterialId(), form.getVersion().trim(), excludeId)) {
            throw BusinessException.ofKey("erp.mrp.routing-version-exists", form.getVersion());
        }
    }

    private boolean versionExists(Long materialId, String version, Long excludeId) {
        Long count = routingMapper.selectCount(Wrappers.lambdaQuery(MrpRouting.class)
                .eq(MrpRouting::getMaterialId, materialId)
                .eq(MrpRouting::getVersion, version)
                .ne(excludeId != null, MrpRouting::getId, excludeId));
        return count != null && count > 0;
    }

    private RoutingVO toVO(MrpRouting routing, Long stepCount, Map<Long, MaterialBriefVO> materials) {
        RoutingVO vo = new RoutingVO();
        vo.setId(routing.getId());
        vo.setMaterialId(routing.getMaterialId());
        MaterialBriefVO m = materials.get(routing.getMaterialId());
        if (m != null) {
            vo.setMaterialCode(m.getCode());
            vo.setMaterialName(m.getName());
            vo.setUnit(m.getUnit());
        }
        vo.setVersion(routing.getVersion());
        vo.setStatus(routing.getStatus());
        vo.setRemark(routing.getRemark());
        vo.setCreateTime(routing.getCreateTime());
        vo.setStepCount(stepCount);
        return vo;
    }
}
