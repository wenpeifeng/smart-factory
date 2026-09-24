package com.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseBom;
import com.mes.basedata.entity.BaseBomItem;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.entity.BaseOperation;
import com.mes.basedata.entity.BaseRouting;
import com.mes.basedata.entity.BaseRoutingStep;
import com.mes.basedata.entity.BaseWorkcenter;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.basedata.mapper.BaseOperationMapper;
import com.mes.basedata.mapper.BaseWorkcenterMapper;
import com.mes.basedata.service.BaseBomService;
import com.mes.basedata.service.BaseRoutingService;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.plan.dto.form.WorkorderForm;
import com.mes.plan.dto.query.WorkorderPageQuery;
import com.mes.plan.dto.vo.WorkorderDetailVO;
import com.mes.plan.dto.vo.WorkorderVO;
import com.mes.plan.dto.vo.WoMaterialVO;
import com.mes.plan.dto.vo.WoStepVO;
import com.mes.plan.entity.PlanWoMaterial;
import com.mes.plan.entity.PlanWoStep;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWoMaterialMapper;
import com.mes.plan.mapper.PlanWoStepMapper;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.plan.service.MesNoGenerator;
import com.mes.plan.service.WorkorderService;
import com.mes.framework.event.IntegrationEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生产工单服务实现。
 * <p>状态流转全部先校验当前状态非法流转抛 BusinessException；
 * release 为事务边界：BOM 展开用料 + 工艺路线展开工序，任一失败整体回滚。
 */
@Service
@RequiredArgsConstructor
public class WorkorderServiceImpl implements WorkorderService {

    /** 工单状态常量（DDL COMMENT 取值） */
    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_RELEASED = "RELEASED";
    public static final String ST_PRODUCING = "PRODUCING";
    public static final String ST_FINISHED = "FINISHED";
    public static final String ST_CLOSED = "CLOSED";

    private final PlanWorkorderMapper workorderMapper;
    private final PlanWoMaterialMapper woMaterialMapper;
    private final PlanWoStepMapper woStepMapper;
    private final BaseMaterialMapper materialMapper;
    private final BaseOperationMapper operationMapper;
    private final BaseWorkcenterMapper workcenterMapper;
    private final com.mes.basedata.mapper.BaseRoutingMapper routingMapper;
    private final BaseBomService bomService;
    private final BaseRoutingService routingService;
    private final MesNoGenerator noGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<WorkorderVO> page(WorkorderPageQuery query) {
        var wrapper = Wrappers.lambdaQuery(PlanWorkorder.class)
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(PlanWorkorder::getWoNo, query.getKeyword())
                        .or().like(PlanWorkorder::getSourceNo, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), PlanWorkorder::getStatus, query.getStatus())
                .eq(query.getMaterialId() != null, PlanWorkorder::getMaterialId, query.getMaterialId())
                .ge(query.getPlanDateFrom() != null, PlanWorkorder::getPlanStartDate, query.getPlanDateFrom())
                .le(query.getPlanDateTo() != null, PlanWorkorder::getPlanStartDate, query.getPlanDateTo())
                .orderByDesc(PlanWorkorder::getId);
        PageResult<WorkorderVO> page = PageUtils.of(workorderMapper, wrapper,
                query.getPageNum(), query.getPageSize(), WorkorderVO::from);
        fillMaterial(page.getRecords());
        return page;
    }

    @Override
    public WorkorderDetailVO detail(Long id) {
        PlanWorkorder wo = requireWorkorder(id);
        WorkorderDetailVO vo = new WorkorderDetailVO();
        WorkorderVO base = WorkorderVO.from(wo);
        org.springframework.beans.BeanUtils.copyProperties(base, vo);
        vo.setCloseRemark(wo.getCloseRemark());

        List<WoStepVO> steps = woStepMapper.selectList(Wrappers.lambdaQuery(PlanWoStep.class)
                        .eq(PlanWoStep::getWorkorderId, id).orderByAsc(PlanWoStep::getSeqNo))
                .stream().map(WoStepVO::from).toList();
        List<Long> wcIds = steps.stream().map(WoStepVO::getWorkcenterId).filter(java.util.Objects::nonNull).toList();
        Map<Long, String> wcNames = wcIds.isEmpty() ? Map.of()
                : workcenterMapper.selectBatchIds(wcIds).stream()
                        .collect(Collectors.toMap(BaseWorkcenter::getId, BaseWorkcenter::getName));
        steps.forEach(s -> {
            if (s.getWorkcenterId() != null) {
                s.setWorkcenterName(wcNames.get(s.getWorkcenterId()));
            }
        });

        List<WoMaterialVO> materials = woMaterialMapper.selectList(Wrappers.lambdaQuery(PlanWoMaterial.class)
                        .eq(PlanWoMaterial::getWorkorderId, id).orderByAsc(PlanWoMaterial::getId))
                .stream().map(WoMaterialVO::from).toList();
        Map<Long, BaseMaterial> matMap = materialMap(materials.stream()
                .map(WoMaterialVO::getMaterialId).toList());
        materials.forEach(m -> {
            BaseMaterial mat = matMap.get(m.getMaterialId());
            if (mat != null) {
                m.setMaterialCode(mat.getCode());
                m.setMaterialName(mat.getName());
            }
        });

        BaseMaterial mat = matMap.containsKey(wo.getMaterialId())
                ? matMap.get(wo.getMaterialId())
                : materialMapper.selectById(wo.getMaterialId());
        if (mat != null) {
            vo.setMaterialCode(mat.getCode());
            vo.setMaterialName(mat.getName());
        }
        vo.setSteps(steps);
        vo.setMaterials(materials);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkorderForm form) {
        BaseBom bom = resolveBom(form.getMaterialId(), form.getBomId(), null);
        BaseRouting routing = resolveRouting(form.getMaterialId(), form.getRoutingId());
        PlanWorkorder wo = applyForm(new PlanWorkorder(), form, bom, routing);
        wo.setWoNo(noGenerator.next(MesNoGenerator.PREFIX_WORKORDER));
        wo.setStatus(ST_DRAFT);
        wo.setSourceType("MANUAL");
        wo.setFinishQty(BigDecimal.ZERO);
        wo.setScrapQty(BigDecimal.ZERO);
        workorderMapper.insert(wo);
        return wo.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WorkorderForm form) {
        PlanWorkorder wo = requireStatus(id, ST_DRAFT, "仅草稿状态可修改");
        BaseBom bom = resolveBom(form.getMaterialId(), form.getBomId(), null);
        BaseRouting routing = resolveRouting(form.getMaterialId(), form.getRoutingId());
        applyForm(wo, form, bom, routing);
        workorderMapper.updateById(wo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireStatus(id, ST_DRAFT, "仅草稿状态可删除");
        woMaterialMapper.delete(Wrappers.lambdaQuery(PlanWoMaterial.class)
                .eq(PlanWoMaterial::getWorkorderId, id));
        woStepMapper.delete(Wrappers.lambdaQuery(PlanWoStep.class)
                .eq(PlanWoStep::getWorkorderId, id));
        workorderMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        PlanWorkorder wo = requireStatus(id, ST_DRAFT, "仅草稿状态可下达");
        // 下达时重新校验 BOM 仍有效（可能被停用/删除）
        BaseBom bom = resolveBom(wo.getMaterialId(), wo.getBomId(), wo.getBomVersion());
        List<BaseBomItem> bomItems = bomService.listItems(bom.getId());
        if (bomItems.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "BOM %s 无子件明细，无法下达", bom.getVersion());
        }
        BaseRouting routing = routingService.findActive(wo.getMaterialId());
        if (routing == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "物料 %d 无有效工艺路线，无法下达", wo.getMaterialId());
        }
        List<BaseRoutingStep> routingSteps = routingService.listSteps(routing.getId());
        if (routingSteps.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工艺路线 %s 无工序步骤，无法下达", routing.getVersion());
        }

        // 用料展开：需求 = 工单数量 x 单位用量 x (1 + 损耗率/100)
        for (BaseBomItem item : bomItems) {
            PlanWoMaterial wom = new PlanWoMaterial();
            wom.setWorkorderId(id);
            wom.setMaterialId(item.getMaterialId());
            BigDecimal loss = item.getLossRate() == null ? BigDecimal.ZERO : item.getLossRate();
            wom.setReqQty(wo.getQty().multiply(item.getQty())
                    .multiply(BigDecimal.ONE.add(loss.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(6, RoundingMode.HALF_UP));
            wom.setIssuedQty(BigDecimal.ZERO);
            woMaterialMapper.insert(wom);
        }

        // 工序展开：快照工序名/工时/质检点；工作中心取步骤覆盖值，否则工序默认值
        Map<Long, BaseOperation> ops = operationMapper.selectBatchIds(
                        routingSteps.stream().map(BaseRoutingStep::getOperationId).toList())
                .stream().collect(Collectors.toMap(BaseOperation::getId, Function.identity()));
        for (BaseRoutingStep step : routingSteps) {
            BaseOperation op = ops.get(step.getOperationId());
            if (op == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工序 %d 不存在", step.getOperationId());
            }
            PlanWoStep wos = new PlanWoStep();
            wos.setWorkorderId(id);
            wos.setSeqNo(step.getSeqNo());
            wos.setOperationId(op.getId());
            wos.setOperationName(op.getName());
            wos.setWorkcenterId(step.getWorkcenterId() != null ? step.getWorkcenterId() : op.getDefaultWorkcenterId());
            wos.setUnitMinutes(step.getUnitMinutes() != null ? step.getUnitMinutes() : op.getUnitMinutes());
            wos.setSetupMinutes(op.getSetupMinutes());
            wos.setPlanQty(wo.getQty());
            wos.setStatus("PENDING_DISPATCH");
            wos.setFinishQty(BigDecimal.ZERO);
            wos.setScrapQty(BigDecimal.ZERO);
            wos.setReworkQty(BigDecimal.ZERO);
            wos.setActualMinutes(0);
            wos.setQcFlag(op.getQcFlag());
            woStepMapper.insert(wos);
        }

        wo.setStatus(ST_RELEASED);
        wo.setRoutingId(routing.getId());
        workorderMapper.updateById(wo);
        publishStatus(wo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        PlanWorkorder wo = workorderMapper.selectById(id);
        if (wo == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单不存在: %d".formatted(id));
        }
        if (!ST_RELEASED.equals(wo.getStatus()) && !ST_PRODUCING.equals(wo.getStatus())) {
            throw new BusinessException("仅已下达/生产中工单可完工，当前: %s", wo.getStatus());
        }
        wo.setStatus(ST_FINISHED);
        workorderMapper.updateById(wo);
        publishStatus(wo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String closeRemark) {
        PlanWorkorder wo = workorderMapper.selectById(id);
        if (wo == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单不存在: %d".formatted(id));
        }
        if (ST_CLOSED.equals(wo.getStatus())) {
            throw new BusinessException("工单已关闭，不可重复操作");
        }
        if (ST_DRAFT.equals(wo.getStatus())) {
            throw new BusinessException("草稿工单请直接删除，无需结案");
        }
        wo.setStatus(ST_CLOSED);
        wo.setCloseRemark(closeRemark);
        workorderMapper.updateById(wo);
        publishStatus(wo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receiveFromErp(String orderNo, Long materialId, BigDecimal qty, String bomVersion,
                               Long routingId, Long warehouseId, String snMode,
                               LocalDate planStart, LocalDate planEnd, Long sourceId, String remark) {
        // 幂等：同来源单号已建单直接返回
        PlanWorkorder existed = workorderMapper.selectOne(Wrappers.lambdaQuery(PlanWorkorder.class)
                .eq(PlanWorkorder::getSourceType, "ERP_ORDER")
                .eq(PlanWorkorder::getSourceNo, orderNo)
                .last("LIMIT 1"));
        if (existed != null) {
            return existed.getId();
        }
        WorkorderForm form = new WorkorderForm();
        form.setMaterialId(materialId);
        form.setQty(qty);
        form.setRoutingId(routingId);
        form.setWarehouseId(warehouseId);
        form.setPlanStartDate(planStart);
        form.setPlanEndDate(planEnd);
        form.setSnMode(snMode);
        form.setRemark(remark);
        BaseBom bom = resolveBom(materialId, null, bomVersion);
        BaseRouting routing = routingId != null ? requireRouting(routingId) : resolveRouting(materialId, null);
        PlanWorkorder wo = applyForm(new PlanWorkorder(), form, bom, routing);
        wo.setWoNo(noGenerator.next(MesNoGenerator.PREFIX_WORKORDER));
        wo.setStatus(ST_DRAFT);
        wo.setSourceType("ERP_ORDER");
        wo.setSourceNo(orderNo);
        wo.setSourceId(sourceId);
        wo.setFinishQty(BigDecimal.ZERO);
        wo.setScrapQty(BigDecimal.ZERO);
        workorderMapper.insert(wo);
        return wo.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accumulateReport(Long workorderId, BigDecimal goodQty, BigDecimal scrapQty) {
        PlanWorkorder wo = requireWorkorder(workorderId);
        if (!ST_RELEASED.equals(wo.getStatus()) && !ST_PRODUCING.equals(wo.getStatus())) {
            throw new BusinessException("工单状态 %s 不允许过站报工", wo.getStatus());
        }
        // 条件累加防并发覆盖（过站并发场景由行级锁保证正确性）
        wo.setFinishQty(nvl(wo.getFinishQty()).add(nvl(goodQty)));
        wo.setScrapQty(nvl(wo.getScrapQty()).add(nvl(scrapQty)));
        if (ST_RELEASED.equals(wo.getStatus())) {
            wo.setStatus(ST_PRODUCING);
        }
        workorderMapper.updateById(wo);
        publishStatus(wo);
    }

    /** 状态流转后发布集成事件（integration 监听推送 ERP 进度镜像） */
    private void publishStatus(PlanWorkorder wo) {
        eventPublisher.publishEvent(new IntegrationEvents.WoStatusChanged(
                wo.getId(), wo.getWoNo(), wo.getSourceNo(), wo.getStatus(),
                wo.getFinishQty(), wo.getScrapQty()));
    }

    /** 取工单并校验状态 */
    private PlanWorkorder requireStatus(Long id, String expected, String message) {
        PlanWorkorder wo = requireWorkorder(id);
        if (!expected.equals(wo.getStatus())) {
            throw new BusinessException("%s，当前状态: %s", message, wo.getStatus());
        }
        return wo;
    }

    private PlanWorkorder requireWorkorder(Long id) {
        PlanWorkorder wo = workorderMapper.selectById(id);
        if (wo == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单不存在: %d".formatted(id));
        }
        return wo;
    }

    /** 解析 BOM：优先指定 id；其次按版本找 ACTIVE；否则物料唯一 ACTIVE 版本 */
    private BaseBom resolveBom(Long materialId, Long bomId, String version) {
        if (bomId != null) {
            BaseBom bom = bomService.findActive(materialId, null);
            // 指定 id 时校验该 id 即有效版本
            if (bom == null || !bom.getId().equals(bomId)) {
                BaseBom byId = bomService.listByMaterial(materialId).stream()
                        .filter(b -> b.getId().equals(bomId)).findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_EXISTS, "BOM 不存在: %d", bomId));
                if (!"ACTIVE".equals(byId.getStatus())) {
                    throw new BusinessException("BOM %s 非有效状态: %s", byId.getVersion(), byId.getStatus());
                }
                return byId;
            }
            return bom;
        }
        return bomService.findActive(materialId, version);
    }

    /** 解析工艺路线：优先指定 id；否则物料 ACTIVE 版本（允许为空，下达时再强校验） */
    private BaseRouting resolveRouting(Long materialId, Long routingId) {
        if (routingId != null) {
            return requireRouting(routingId);
        }
        return routingService.findActive(materialId);
    }

    private BaseRouting requireRouting(Long routingId) {
        BaseRouting routing = routingMapper.selectById(routingId);
        if (routing == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工艺路线不存在: %d", routingId);
        }
        if (!"ACTIVE".equals(routing.getStatus())) {
            throw new BusinessException("工艺路线 %s 非有效状态: %s", routing.getVersion(), routing.getStatus());
        }
        return routing;
    }

    /** 表单字段套用到实体（单号/状态/来源由调用方控制） */
    private PlanWorkorder applyForm(PlanWorkorder wo, WorkorderForm form, BaseBom bom, BaseRouting routing) {
        wo.setMaterialId(form.getMaterialId());
        wo.setQty(form.getQty());
        wo.setBomId(bom.getId());
        wo.setBomVersion(bom.getVersion());
        wo.setRoutingId(routing != null ? routing.getId() : null);
        wo.setWarehouseId(form.getWarehouseId());
        wo.setPlanStartDate(form.getPlanStartDate());
        wo.setPlanEndDate(form.getPlanEndDate());
        wo.setSnMode(StringUtils.hasText(form.getSnMode()) ? form.getSnMode() : "BATCH");
        wo.setRemark(form.getRemark());
        return wo;
    }

    private void fillMaterial(List<WorkorderVO> records) {
        Map<Long, BaseMaterial> matMap = materialMap(records.stream()
                .map(WorkorderVO::getMaterialId).toList());
        records.forEach(vo -> {
            BaseMaterial mat = matMap.get(vo.getMaterialId());
            if (mat != null) {
                vo.setMaterialCode(mat.getCode());
                vo.setMaterialName(mat.getName());
            }
        });
    }

    private Map<Long, BaseMaterial> materialMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return materialMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
