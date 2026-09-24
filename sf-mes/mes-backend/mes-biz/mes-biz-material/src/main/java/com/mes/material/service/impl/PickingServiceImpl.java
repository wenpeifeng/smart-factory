package com.mes.material.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.integration.erp.dto.ErpPickingDTO;
import com.mes.integration.service.ErpPushService;
import com.mes.material.dto.form.PickingForm;
import com.mes.material.dto.vo.PickingVO;
import com.mes.material.entity.MatPicking;
import com.mes.material.entity.MatPickingItem;
import com.mes.material.mapper.MatPickingItemMapper;
import com.mes.material.mapper.MatPickingMapper;
import com.mes.material.service.PickingService;
import com.mes.material.service.ShopStockService;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.plan.service.MesNoGenerator;
import com.mes.plan.service.WoMaterialService;
import com.mes.framework.event.TraceEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 领料单服务实现。
 * <p>审核事务：状态置 AUDITED → 线边仓入库（默认工作中心维度用 0 表示车间总线边）
 * → 回写 plan_wo_material.issued_qty → 回传 ERP（失败不阻断，推送日志重试）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickingServiceImpl implements PickingService {

    /** 线边仓默认维度（未指定工作中心时统一挂 0=车间总线边仓） */
    private static final Long DEFAULT_WC = 0L;

    private final MatPickingMapper pickingMapper;
    private final MatPickingItemMapper pickingItemMapper;
    private final PlanWorkorderMapper workorderMapper;
    private final BaseMaterialMapper materialMapper;
    private final ShopStockService shopStockService;
    private final MesNoGenerator noGenerator;
    private final WoMaterialService woMaterialService;
    private final ErpPushService erpPushService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PickingForm form) {
        PlanWorkorder wo = requireWorkorder(form.getWorkorderId());
        MatPicking picking = new MatPicking();
        picking.setPickNo(noGenerator.next(MesNoGenerator.PREFIX_PICKING));
        picking.setWorkorderId(wo.getId());
        picking.setWoNo(wo.getWoNo());
        picking.setWarehouseId(form.getWarehouseId());
        picking.setStatus("DRAFT");
        picking.setPickType(StringUtils.hasText(form.getPickType()) ? form.getPickType() : "NORMAL");
        picking.setRemark(form.getRemark());
        picking.setErpPushed(0);
        pickingMapper.insert(picking);
        for (PickingForm.Item item : form.getItems()) {
            MatPickingItem row = new MatPickingItem();
            row.setPickingId(picking.getId());
            row.setMaterialId(item.getMaterialId());
            row.setBatchNo("-".equals(item.getBatchNo()) ? "" : item.getBatchNo());
            row.setQty(item.getQty());
            row.setRemark(item.getRemark());
            pickingItemMapper.insert(row);
        }
        return picking.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatPicking picking = requirePicking(id);
        if (!"DRAFT".equals(picking.getStatus())) {
            throw new BusinessException("仅草稿领料单可删除");
        }
        pickingItemMapper.delete(Wrappers.lambdaQuery(MatPickingItem.class)
                .eq(MatPickingItem::getPickingId, id));
        pickingMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        MatPicking picking = requirePicking(id);
        if (!"DRAFT".equals(picking.getStatus())) {
            throw new BusinessException("领料单已审核，不可重复操作");
        }
        List<MatPickingItem> items = pickingItemMapper.selectList(
                Wrappers.lambdaQuery(MatPickingItem.class).eq(MatPickingItem::getPickingId, id));
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "领料单 %s 无行项目", picking.getPickNo());
        }
        for (MatPickingItem item : items) {
            shopStockService.stockIn(item.getMaterialId(), item.getBatchNo(), DEFAULT_WC, item.getQty());
            // 回写工单用料已领数（超出需求时报错回滚整张领料单）
            woMaterialService.addIssued(picking.getWorkorderId(), item.getMaterialId(), item.getQty());
        }
        // 发布上料追溯事件（trace 模块 AFTER_COMMIT 落 trace_link）
        eventPublisher.publishEvent(new TraceEvents.PickingAudited(
                picking.getId(), picking.getWorkorderId(),
                items.stream().map(i -> new TraceEvents.PickingAudited.Item(
                        i.getBatchNo(), i.getMaterialId(), i.getQty())).toList(),
                picking.getAuditTime()));
        picking.setStatus("AUDITED");
        picking.setAuditTime(LocalDateTime.now());
        picking.setAuditor("系统");
        pickingMapper.updateById(picking);

        // 回传 ERP（幂等 + 失败重试由推送日志保证；此处失败不回滚领料）
        try {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            items.stream().map(MatPickingItem::getMaterialId).toList())
                    .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
            ErpPickingDTO dto = new ErpPickingDTO();
            dto.setPickNo(picking.getPickNo());
            dto.setWoNo(picking.getWoNo());
            dto.setWarehouseCode(String.valueOf(picking.getWarehouseId()));
            dto.setItems(items.stream().map(i -> {
                ErpPickingDTO.Item di = new ErpPickingDTO.Item();
                di.setMaterialCode(mats.containsKey(i.getMaterialId())
                        ? mats.get(i.getMaterialId()).getCode() : String.valueOf(i.getMaterialId()));
                di.setBatchNo(i.getBatchNo());
                di.setQty(i.getQty());
                return di;
            }).toList());
            erpPushService.pushPicking(picking.getId(), dto);
            picking.setErpPushed(1);
            pickingMapper.updateById(picking);
        } catch (Exception ex) {
            log.warn("[PICKING] 领料回传 ERP 失败（不影响审核）: {}", ex.getMessage());
        }
    }

    @Override
    public PageResult<PickingVO> page(long pageNum, long pageSize, String keyword, String status) {
        var wrapper = Wrappers.lambdaQuery(MatPicking.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(MatPicking::getPickNo, keyword)
                        .or().like(MatPicking::getWoNo, keyword))
                .eq(StringUtils.hasText(status), MatPicking::getStatus, status)
                .orderByDesc(MatPicking::getId);
        return PageUtils.of(pickingMapper, wrapper, pageNum, pageSize, PickingVO::from);
    }

    @Override
    public PickingVO detail(Long id) {
        PickingVO vo = PickingVO.from(requirePicking(id));
        List<PickingVO.ItemVO> items = pickingItemMapper.selectList(
                        Wrappers.lambdaQuery(MatPickingItem.class).eq(MatPickingItem::getPickingId, id))
                .stream().map(PickingVO.ItemVO::from).toList();
        if (!items.isEmpty()) {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            items.stream().map(PickingVO.ItemVO::getMaterialId).toList())
                    .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
            items.forEach(i -> {
                BaseMaterial mat = mats.get(i.getMaterialId());
                if (mat != null) {
                    i.setMaterialCode(mat.getCode());
                    i.setMaterialName(mat.getName());
                }
            });
        }
        vo.setItems(items);
        return vo;
    }

    private MatPicking requirePicking(Long id) {
        MatPicking picking = pickingMapper.selectById(id);
        if (picking == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "领料单不存在: %d".formatted(id));
        }
        return picking;
    }

    private PlanWorkorder requireWorkorder(Long id) {
        PlanWorkorder wo = workorderMapper.selectById(id);
        if (wo == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单不存在: %d".formatted(id));
        }
        return wo;
    }
}
