package com.mes.integration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseBom;
import com.mes.basedata.entity.BaseBomItem;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.entity.BaseOperation;
import com.mes.basedata.entity.BaseRouting;
import com.mes.basedata.entity.BaseRoutingStep;
import com.mes.basedata.entity.BaseWarehouse;
import com.mes.basedata.mapper.BaseBomItemMapper;
import com.mes.basedata.mapper.BaseBomMapper;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.basedata.mapper.BaseOperationMapper;
import com.mes.basedata.mapper.BaseRoutingMapper;
import com.mes.basedata.mapper.BaseRoutingStepMapper;
import com.mes.basedata.mapper.BaseWarehouseMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.integration.erp.dto.ErpBomDTO;
import com.mes.integration.erp.dto.ErpMaterialDTO;
import com.mes.integration.erp.dto.ErpProductionOrderDTO;
import com.mes.integration.erp.dto.ErpRoutingDTO;
import com.mes.integration.service.ErpInboundService;
import com.mes.plan.service.WorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * ERP 入站落地实现。
 * <p>幂等策略：订单按 source_no、物料按 code、BOM 按物料+版本、工艺路线按编码。
 * ERP 下推主数据即已审核（ACTIVE/启用），重复下推幂等覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpInboundServiceImpl implements ErpInboundService {

    private final BaseMaterialMapper materialMapper;
    private final BaseBomMapper bomMapper;
    private final BaseBomItemMapper bomItemMapper;
    private final BaseRoutingMapper routingMapper;
    private final BaseRoutingStepMapper routingStepMapper;
    private final BaseOperationMapper operationMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final com.mes.basedata.mapper.BaseWorkcenterMapper workcenterMapper;
    private final WorkorderService workorderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receiveProductionOrder(ErpProductionOrderDTO dto) {
        BaseMaterial material = requireMaterialByCode(dto.getMaterialCode());
        Long warehouseId = null;
        if (StringUtils.hasText(dto.getWarehouseCode())) {
            BaseWarehouse wh = warehouseMapper.selectOne(Wrappers.lambdaQuery(BaseWarehouse.class)
                    .eq(BaseWarehouse::getCode, dto.getWarehouseCode()).last("LIMIT 1"));
            warehouseId = wh != null ? wh.getId() : null;
        }
        Long workorderId = workorderService.receiveFromErp(dto.getOrderNo(), material.getId(), dto.getQty(),
                dto.getBomVersion(), null, warehouseId,
                StringUtils.hasText(dto.getSnMode()) ? dto.getSnMode() : "BATCH",
                dto.getPlanStartDate(), dto.getPlanEndDate(), dto.getOrderNo().hashCode() * -1L, dto.getRemark());
        log.info("[ERP-IN] 生产订单下推: orderNo={} -> workorderId={}", dto.getOrderNo(), workorderId);
        return workorderId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receiveMaterial(ErpMaterialDTO dto) {
        BaseMaterial material = materialMapper.selectOne(Wrappers.lambdaQuery(BaseMaterial.class)
                .eq(BaseMaterial::getCode, dto.getCode()).last("LIMIT 1"));
        if (material == null) {
            material = new BaseMaterial();
            material.setCode(dto.getCode());
            material.setName(dto.getName());
            material.setSpec(dto.getSpec());
            material.setStatus(dto.getStatus() != null && dto.getStatus() == 0 ? 0 : 1);
            material.setRemark(dto.getRemark());
            materialMapper.insert(material);
            log.info("[ERP-IN] 物料新增: {}", dto.getCode());
            return material.getId();
        }
        material.setName(dto.getName());
        material.setSpec(dto.getSpec());
        if (dto.getStatus() != null) {
            material.setStatus(dto.getStatus());
        }
        material.setRemark(dto.getRemark());
        materialMapper.updateById(material);
        return material.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receiveBom(ErpBomDTO dto) {
        BaseMaterial product = requireMaterialByCode(dto.getMaterialCode());
        BaseBom bom = bomMapper.selectOne(Wrappers.lambdaQuery(BaseBom.class)
                .eq(BaseBom::getMaterialId, product.getId())
                .eq(BaseBom::getVersion, dto.getVersion())
                .last("LIMIT 1"));
        if (bom == null) {
            bom = new BaseBom();
            bom.setMaterialId(product.getId());
            bom.setVersion(dto.getVersion());
            bomMapper.insert(bom);
        }
        // ERP 侧下推的 BOM 即已审核主数据，直接置 ACTIVE（幂等重复下推亦刷新为生效）
        bom.setStatus("ACTIVE");
        bomMapper.updateById(bom);
        bomItemMapper.delete(Wrappers.lambdaQuery(BaseBomItem.class).eq(BaseBomItem::getBomId, bom.getId()));
        for (ErpBomDTO.Item item : dto.getItems()) {
            BaseMaterial component = requireMaterialByCode(item.getMaterialCode());
            BaseBomItem row = new BaseBomItem();
            row.setBomId(bom.getId());
            row.setMaterialId(component.getId());
            row.setQty(item.getQty());
            row.setLossRate(item.getLossRate());
            bomItemMapper.insert(row);
        }
        log.info("[ERP-IN] BOM 下推: {} v{} -> bomId={}", dto.getMaterialCode(), dto.getVersion(), bom.getId());
        return bom.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receiveRouting(ErpRoutingDTO dto) {
        // 说明：base_routing 无编码列，用 remark 约定前缀 "ERP:{routingCode}" 作为下推幂等键
        String erpKey = "ERP:" + dto.getRoutingCode();
        BaseRouting routing = routingMapper.selectOne(Wrappers.lambdaQuery(BaseRouting.class)
                .eq(BaseRouting::getRemark, erpKey).last("LIMIT 1"));
        // 绑定成品物料（提供 materialCode 时）；未提供挂 0=通用模板，工单下达不会命中
        Long materialId = 0L;
        if (StringUtils.hasText(dto.getMaterialCode())) {
            materialId = requireMaterialByCode(dto.getMaterialCode()).getId();
        }
        if (routing == null) {
            routing = new BaseRouting();
            routing.setMaterialId(materialId);
            routing.setVersion(dto.getRoutingCode());
            routing.setRemark(erpKey);
            routingMapper.insert(routing);
        } else {
            routing.setMaterialId(materialId);
        }
        // ERP 下推的工艺路线即已审核，直接置 ACTIVE
        routing.setStatus("ACTIVE");
        routingMapper.updateById(routing);
        routingStepMapper.delete(Wrappers.lambdaQuery(BaseRoutingStep.class)
                .eq(BaseRoutingStep::getRoutingId, routing.getId()));
        for (ErpRoutingDTO.Step step : dto.getSteps()) {
            BaseOperation op = upsertOperation(step);
            BaseRoutingStep row = new BaseRoutingStep();
            row.setRoutingId(routing.getId());
            row.setSeqNo(step.getSeqNo());
            row.setOperationId(op.getId());
            if (StringUtils.hasText(step.getWorkcenterCode())) {
                // 工作中心按编码匹配，未匹配保持空（取工序默认）
                com.mes.basedata.entity.BaseWorkcenter wc = workcenterMapper.selectOne(
                        Wrappers.lambdaQuery(com.mes.basedata.entity.BaseWorkcenter.class)
                                .eq(com.mes.basedata.entity.BaseWorkcenter::getCode, step.getWorkcenterCode())
                                .last("LIMIT 1"));
                row.setWorkcenterId(wc != null ? wc.getId() : null);
            }
            row.setUnitMinutes(step.getUnitMinutes());
            routingStepMapper.insert(row);
        }
        log.info("[ERP-IN] 工艺路线下推: {} -> routingId={}", dto.getRoutingCode(), routing.getId());
        return routing.getId();
    }

    /** 工序按名称 upsert（编码由 ERP 给出则按编码匹配） */
    private BaseOperation upsertOperation(ErpRoutingDTO.Step step) {
        BaseOperation op = operationMapper.selectOne(Wrappers.lambdaQuery(BaseOperation.class)
                .eq(BaseOperation::getCode, step.getOperationCode()).last("LIMIT 1"));
        if (op == null) {
            op = operationMapper.selectOne(Wrappers.lambdaQuery(BaseOperation.class)
                    .eq(BaseOperation::getName, step.getOperationName()).last("LIMIT 1"));
        }
        if (op == null) {
            op = new BaseOperation();
            op.setCode(step.getOperationCode());
            op.setName(step.getOperationName());
            op.setSetupMinutes(step.getSetupMinutes() != null ? step.getSetupMinutes() : 0);
            op.setUnitMinutes(step.getUnitMinutes() != null ? step.getUnitMinutes() : java.math.BigDecimal.ZERO);
            op.setQcFlag(step.getQcFlag() != null ? step.getQcFlag() : 0);
            op.setStatus("ENABLED");
            op.setRemark("ERP 下推建档");
            operationMapper.insert(op);
        }
        return op;
    }

    private BaseMaterial requireMaterialByCode(String code) {
        BaseMaterial material = materialMapper.selectOne(Wrappers.lambdaQuery(BaseMaterial.class)
                .eq(BaseMaterial::getCode, code).last("LIMIT 1"));
        if (material == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "物料不存在，请先下推物料主数据: %s", code);
        }
        return material;
    }
}
