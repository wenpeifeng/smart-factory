package com.erp.integration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BaseMaterialCategory;
import com.erp.basedata.entity.BaseUnit;
import com.erp.basedata.mapper.BaseMaterialCategoryMapper;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.BaseUnitMapper;
import com.erp.integration.dto.MesBomDTO;
import com.erp.integration.dto.MesMaterialDTO;
import com.erp.integration.dto.MesRoutingDTO;
import com.erp.integration.service.MasterdataSyncService;
import com.erp.integration.service.OutboxService;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.entity.MrpBom;
import com.erp.mrp.entity.MrpBomItem;
import com.erp.mrp.entity.MrpOperation;
import com.erp.mrp.entity.MrpRouting;
import com.erp.mrp.entity.MrpRoutingStep;
import com.erp.mrp.entity.MrpWorkCenter;
import com.erp.mrp.mapper.MrpBomItemMapper;
import com.erp.mrp.mapper.MrpBomMapper;
import com.erp.mrp.mapper.MrpOperationMapper;
import com.erp.mrp.mapper.MrpRoutingMapper;
import com.erp.mrp.mapper.MrpRoutingStepMapper;
import com.erp.mrp.mapper.MrpWorkCenterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主数据对账推送实现：ERP 为事实源，每日全量下推使 MES 本地副本收敛。
 * <p>映射口径（ERP 无对应字段时置空，MES 侧 upsert 不覆盖为空的列语义由契约约定）：
 * <ul>
 *   <li>物料：unit/categoryCode 经 ID 换编码；ERP 无 material_type/weight/key_component 列则不填；</li>
 *   <li>BOM：版本取 mrp_bom.version；</li>
 *   <li>工艺路线：ERP 工艺路线无独立编码，routingCode 下推 version（同一物料版本唯一，可作幂等键）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterdataSyncServiceImpl implements MasterdataSyncService {

    private final BaseMaterialMapper materialMapper;
    private final BaseUnitMapper unitMapper;
    private final BaseMaterialCategoryMapper categoryMapper;
    private final MrpBomMapper bomMapper;
    private final MrpBomItemMapper bomItemMapper;
    private final MrpRoutingMapper routingMapper;
    private final MrpRoutingStepMapper routingStepMapper;
    private final MrpOperationMapper operationMapper;
    private final MrpWorkCenterMapper workCenterMapper;
    private final OutboxService outboxService;

    @Override
    public String reconcileAll() {
        int materials = pushAllMaterials();
        int boms = pushAllBoms();
        int routings = pushAllRoutings();
        String summary = "物料 " + materials + " 条、BOM " + boms + " 条、工艺路线 " + routings + " 条已入 outbox";
        log.info("[MES-RECONCILE] 全量对账推送完成: {}", summary);
        return summary;
    }

    @Override
    public int pushAllMaterials() {
        Map<Long, BaseUnit> units = unitMapper.selectList(Wrappers.lambdaQuery(BaseUnit.class)).stream()
                .collect(Collectors.toMap(BaseUnit::getId, Function.identity(), (a, b) -> a));
        Map<Long, BaseMaterialCategory> categories =
                categoryMapper.selectList(Wrappers.lambdaQuery(BaseMaterialCategory.class)).stream()
                        .collect(Collectors.toMap(BaseMaterialCategory::getId, Function.identity(), (a, b) -> a));
        List<BaseMaterial> materials = materialMapper.selectList(Wrappers.lambdaQuery(BaseMaterial.class));
        for (BaseMaterial m : materials) {
            MesMaterialDTO dto = new MesMaterialDTO();
            dto.setCode(m.getCode());
            dto.setName(m.getName());
            dto.setSpec(m.getSpec());
            BaseUnit unit = units.get(m.getUnitId());
            dto.setUnit(unit == null ? null : unit.getName());
            BaseMaterialCategory category = categories.get(m.getCategoryId());
            dto.setCategoryCode(category == null ? null : category.getCode());
            dto.setBatchManaged(m.getBatchManaged());
            dto.setStatus(m.getStatus());
            dto.setRemark(m.getRemark());
            outboxService.enqueue(OutboxService.BIZ_MATERIAL, m.getId(), dto);
        }
        return materials.size();
    }

    @Override
    public int pushAllBoms() {
        List<MrpBom> boms = bomMapper.selectList(Wrappers.lambdaQuery(MrpBom.class)
                .eq(MrpBom::getStatus, MrpConstants.BOM_ACTIVE));
        if (boms.isEmpty()) {
            return 0;
        }
        Map<Long, String> materialCodes = materialCodes();
        List<Long> bomIds = boms.stream().map(MrpBom::getId).toList();
        List<MrpBomItem> items = bomItemMapper.selectList(Wrappers.lambdaQuery(MrpBomItem.class)
                .in(MrpBomItem::getBomId, bomIds));
        Map<Long, List<MrpBomItem>> itemsByBom = items.stream()
                .collect(Collectors.groupingBy(MrpBomItem::getBomId));
        int count = 0;
        for (MrpBom bom : boms) {
            List<MrpBomItem> bomItems = itemsByBom.get(bom.getId());
            if (bomItems == null || bomItems.isEmpty()) {
                continue;
            }
            MesBomDTO dto = new MesBomDTO();
            dto.setMaterialCode(materialCodes.get(bom.getMaterialId()));
            dto.setVersion(bom.getVersion());
            dto.setItems(bomItems.stream().map(i -> {
                MesBomDTO.Item item = new MesBomDTO.Item();
                item.setMaterialCode(materialCodes.get(i.getMaterialId()));
                item.setQty(i.getQty());
                item.setLossRate(i.getLossRate());
                return item;
            }).filter(i -> i.getMaterialCode() != null).toList());
            if (dto.getItems().isEmpty()) {
                continue;
            }
            outboxService.enqueue(OutboxService.BIZ_BOM, bom.getId(), dto);
            count++;
        }
        return count;
    }

    @Override
    public int pushAllRoutings() {
        List<MrpRouting> routings = routingMapper.selectList(Wrappers.lambdaQuery(MrpRouting.class)
                .eq(MrpRouting::getStatus, MrpConstants.BOM_ACTIVE));
        if (routings.isEmpty()) {
            return 0;
        }
        Map<Long, String> materialCodes = materialCodes();
        Map<Long, MrpOperation> operations = operationMapper.selectList(
                        Wrappers.lambdaQuery(MrpOperation.class)).stream()
                .collect(Collectors.toMap(MrpOperation::getId, Function.identity(), (a, b) -> a));
        Map<Long, MrpWorkCenter> workCenters = workCenterMapper.selectList(
                        Wrappers.lambdaQuery(MrpWorkCenter.class)).stream()
                .collect(Collectors.toMap(MrpWorkCenter::getId, Function.identity(), (a, b) -> a));
        List<Long> routingIds = routings.stream().map(MrpRouting::getId).toList();
        List<MrpRoutingStep> steps = routingStepMapper.selectList(Wrappers.lambdaQuery(MrpRoutingStep.class)
                .in(MrpRoutingStep::getRoutingId, routingIds)
                .orderByAsc(MrpRoutingStep::getSeqNo));
        Map<Long, List<MrpRoutingStep>> stepsByRouting = steps.stream()
                .collect(Collectors.groupingBy(MrpRoutingStep::getRoutingId));
        Map<Long, String> materialNames = materialMapper.selectList(Wrappers.lambdaQuery(BaseMaterial.class))
                .stream().collect(Collectors.toMap(BaseMaterial::getId, BaseMaterial::getName, (a, b) -> a));
        int count = 0;
        for (MrpRouting routing : routings) {
            List<MrpRoutingStep> routingSteps = stepsByRouting.get(routing.getId());
            if (routingSteps == null || routingSteps.isEmpty()) {
                continue;
            }
            MesRoutingDTO dto = new MesRoutingDTO();
            dto.setRoutingCode(routing.getVersion());
            dto.setRoutingName(materialNames.getOrDefault(routing.getMaterialId(), "") + " 工艺路线 " + routing.getVersion());
            dto.setMaterialCode(materialCodes.get(routing.getMaterialId()));
            dto.setSteps(routingSteps.stream().map(s -> {
                MrpOperation op = operations.get(s.getOperationId());
                if (op == null) {
                    return null;
                }
                MesRoutingDTO.Step step = new MesRoutingDTO.Step();
                step.setSeqNo(s.getSeqNo());
                step.setOperationCode(op.getCode());
                step.setOperationName(op.getName());
                MrpWorkCenter wc = s.getWorkCenterId() == null ? null : workCenters.get(s.getWorkCenterId());
                step.setWorkcenterCode(wc == null ? null : wc.getCode());
                step.setUnitMinutes(s.getUnitMinutes() != null ? s.getUnitMinutes() : op.getUnitMinutes());
                step.setSetupMinutes(op.getSetupMinutes());
                step.setQcFlag(s.getReportFlag());
                return step;
            }).filter(Objects::nonNull).toList());
            if (dto.getSteps().isEmpty()) {
                continue;
            }
            outboxService.enqueue(OutboxService.BIZ_ROUTING, routing.getId(), dto);
            count++;
        }
        return count;
    }

    /** 全部物料的 id -> code 映射（快照，供 BOM/工艺路线换码） */
    private Map<Long, String> materialCodes() {
        return materialMapper.selectList(Wrappers.lambdaQuery(BaseMaterial.class)).stream()
                .collect(Collectors.toMap(BaseMaterial::getId, BaseMaterial::getCode, (a, b) -> a));
    }
}
