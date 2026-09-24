package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.cost.service.CostService;
import com.erp.framework.mybatis.PageUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.service.StockService;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.OutsourceForm;
import com.erp.mrp.dto.form.OutsourceReceiveForm;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.OutsourceItemVO;
import com.erp.mrp.dto.vo.OutsourceVO;
import com.erp.mrp.entity.MrpBom;
import com.erp.mrp.entity.MrpBomItem;
import com.erp.mrp.entity.MrpOutsource;
import com.erp.mrp.entity.MrpOutsourceItem;
import com.erp.mrp.mapper.MrpBomItemMapper;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import com.erp.mrp.mapper.MrpOutsourceItemMapper;
import com.erp.mrp.mapper.MrpOutsourceMapper;
import com.erp.mrp.service.MrpBomService;
import com.erp.mrp.service.MrpOutsourceService;
import com.erp.mrp.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 委外加工服务实现。
 * <p>
 * 发料 = 按 BOM 子件经 {@link StockService#stockOut} 扣发料仓库存（FIFO 批次感知）；
 * 完工入库 = 成品经 {@link StockService#stockIn} 入库 + INSERT fin_payable
 * （数量×加工费单价，财务模块集成点，表未就绪时抛异常整单回滚，不产生"入库无应付"的不一致）。
 * <p>
 * 三模块集成接线（V2101）说明：InvBizType 枚举不含 OUT_USE/OUT_IN（委外专用编码），
 * 委外发料/收货统一并入生产领料/生产入库口径（PROD_USE/PROD_IN）过 StockService 与
 * CostService——批次感知与成本归集因此闭环；委外业务仍可通过流水 biz_no（OS 前缀单号）
 * 与 remark（"委外发料/委外完工入库"）完整追溯，历史 OUT_USE/OUT_IN 流水不受影响。
 */
@Service
@RequiredArgsConstructor
public class MrpOutsourceServiceImpl implements MrpOutsourceService {

    /** 成本模块约定的业务类型编码（与 inventory InvBizType 一致；委外并入生产口径） */
    private static final String COST_BIZ_PROD_USE = "PROD_USE";
    private static final String COST_BIZ_PROD_IN = "PROD_IN";

    private final MrpOutsourceMapper outsourceMapper;
    private final MrpOutsourceItemMapper outsourceItemMapper;
    private final MrpBomItemMapper bomItemMapper;
    private final MrpBomService bomService;
    private final MrpIntegrationMapper integrationMapper;
    private final MrpLookupHelper lookup;
    private final SeqNoService seqNoService;
    /** 库存唯一入口（批次感知 FIFO，替代一期裸 SQL 操作 inv_stock/inv_movement） */
    private final StockService stockService;
    /** 计价唯一入口（同事务实时归集委外用料/入库成本） */
    private final CostService costService;

    @Override
    public PageResult<OutsourceVO> page(long pageNum, long pageSize, String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<Long> materialIds = hasKeyword ? integrationMapper.selectMaterialIds(keyword) : List.of();
        if (hasKeyword && materialIds.isEmpty()) {
            return PageResult.of(List.of(), 0, pageNum, pageSize);
        }
        PageResult<MrpOutsource> page = PageUtils.of(outsourceMapper,
                Wrappers.lambdaQuery(MrpOutsource.class)
                        .eq(status != null && !status.isBlank(), MrpOutsource::getStatus, status)
                        .in(hasKeyword, MrpOutsource::getMaterialId, materialIds)
                        .orderByDesc(MrpOutsource::getId),
                pageNum, pageSize);
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                page.getRecords().stream().map(MrpOutsource::getMaterialId).toList());
        Map<Long, String> supplierNames = lookup.supplierNames(
                page.getRecords().stream().map(MrpOutsource::getSupplierId).toList());
        return page.map(os -> toVO(os, materials, supplierNames, null));
    }

    @Override
    public OutsourceVO detail(Long id) {
        MrpOutsource os = requireOrder(id);
        List<MrpOutsourceItem> items = listItems(id);
        Map<Long, MaterialBriefVO> itemMaterials = lookup.materialMap(
                items.stream().map(MrpOutsourceItem::getMaterialId).toList());
        OutsourceVO vo = toVO(os,
                lookup.materialMap(List.of(os.getMaterialId())),
                lookup.supplierNames(List.of(os.getSupplierId())), null);
        vo.setItems(items.stream().map(item -> {
            OutsourceItemVO itemVO = new OutsourceItemVO();
            itemVO.setId(item.getId());
            itemVO.setMaterialId(item.getMaterialId());
            MaterialBriefVO m = itemMaterials.get(item.getMaterialId());
            if (m != null) {
                itemVO.setMaterialCode(m.getCode());
                itemVO.setMaterialName(m.getName());
                itemVO.setUnit(m.getUnit());
            }
            itemVO.setQty(item.getQty());
            return itemVO;
        }).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OutsourceForm form) {
        MrpBom bom = validateForm(null, form);
        MrpOutsource os = new MrpOutsource();
        applyForm(os, form);
        os.setOrderNo(seqNoService.next(MrpConstants.PREFIX_OUTSOURCE));
        os.setBomId(bom.getId());
        os.setBomVersion(bom.getVersion());
        os.setStatus(MrpConstants.OS_DRAFT);
        outsourceMapper.insert(os);
        insertItems(os.getId(), bom.getId(), form.getQty());
        return os.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OutsourceForm form) {
        MrpOutsource os = requireOrder(id);
        requireDraft(os);
        MrpBom bom = validateForm(id, form);
        MrpOutsource upd = new MrpOutsource();
        upd.setId(id);
        applyForm(upd, form);
        upd.setBomId(bom.getId());
        upd.setBomVersion(bom.getVersion());
        outsourceMapper.updateById(upd);
        outsourceItemMapper.delete(Wrappers.lambdaQuery(MrpOutsourceItem.class)
                .eq(MrpOutsourceItem::getOutsourceId, id));
        insertItems(id, bom.getId(), form.getQty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MrpOutsource os = requireOrder(id);
        requireDraft(os);
        outsourceMapper.deleteById(id);
        outsourceItemMapper.delete(Wrappers.lambdaQuery(MrpOutsourceItem.class)
                .eq(MrpOutsourceItem::getOutsourceId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issueMaterial(Long id) {
        MrpOutsource os = requireOrder(id);
        requireDraft(os);
        if (os.getIssueWarehouseId() == null) {
            throw BusinessException.ofKey("erp.mrp.subcontract-no-issue-warehouse");
        }
        List<MrpOutsourceItem> items = listItems(id);
        if (items.isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.subcontract-no-issue-lines");
        }
        // 逐子件：StockService 策略出库（FIFO 批次拆行；非批次汇总行；不足整单回滚）
        //    + CostService 实时归集发料成本（PROD_USE 口径）
        for (MrpOutsourceItem item : items) {
            if (item.getQty().signum() <= 0) {
                continue;
            }
            stockService.stockOut(os.getIssueWarehouseId(), item.getMaterialId(), item.getQty(),
                    OutStrategy.FIFO, null, InvBizType.PROD_USE, id, os.getOrderNo(),
                    "委外发料 " + os.getOrderNo());
            costService.onStockOut(item.getMaterialId(), os.getIssueWarehouseId(), COST_BIZ_PROD_USE,
                    os.getOrderNo(), id, item.getQty(), null);
        }
        MrpOutsource upd = new MrpOutsource();
        upd.setId(id);
        upd.setStatus(MrpConstants.OS_ISSUED);
        outsourceMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long id, OutsourceReceiveForm form) {
        MrpOutsource os = requireOrder(id);
        if (!MrpConstants.OS_ISSUED.equals(os.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.subcontract-finish-status-not-allowed", os.getStatus());
        }
        Long warehouseId = form.getWarehouseId() != null ? form.getWarehouseId() : os.getInWarehouseId();
        if (warehouseId == null) {
            throw BusinessException.ofKey("erp.mrp.finish-in-warehouse-required2");
        }
        if (form.getGoodQty().compareTo(os.getQty()) > 0) {
            throw BusinessException.ofKey("erp.mrp.finish-in-over-subcontract-qty", form.getGoodQty().stripTrailingZeros().toPlainString(), os.getQty().stripTrailingZeros().toPlainString());
        }
        if (integrationMapper.tableExists("fin_payable") == 0) {
            throw BusinessException.ofKey("erp.mrp.payable-table-not-ready");
        }
        // 成品入库：StockService 批次入库（表单批次号透传）+ CostService 实时计价
        // （PROD_IN 口径，按当前移动加权价估算；委外加工费走应付不计入存货成本）
        stockService.stockIn(warehouseId, os.getMaterialId(), form.getBatchNo(),
                form.getGoodQty(), InvBizType.PROD_IN, id, os.getOrderNo(),
                "委外完工入库 " + os.getOrderNo());
        costService.onStockIn(os.getMaterialId(), warehouseId, COST_BIZ_PROD_IN,
                os.getOrderNo(), id, form.getGoodQty(), null, LocalDate.now());
        // 加工费应付 = 入库数量 × 加工费单价（金额 2 位 HALF_UP；fin_payable 状态 UNSETTLED）
        BigDecimal amount = form.getGoodQty().multiply(os.getFeeUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
        integrationMapper.insertPayable(os.getSupplierId(), os.getOrderNo(), amount,
                "委外加工费 " + os.getOrderNo());
        MrpOutsource upd = new MrpOutsource();
        upd.setId(id);
        upd.setStatus(MrpConstants.OS_RECEIVED);
        outsourceMapper.updateById(upd);
    }

    // ==================== 私有工具 ====================

    /** 表单校验：成品物料/供应商存在、成品有有效 BOM；返回有效 BOM */
    private MrpBom validateForm(Long excludeId, OutsourceForm form) {
        Map<Long, MaterialBriefVO> materials =
                lookup.materialMap(List.of(form.getMaterialId()));
        if (materials.isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.finished-material-not-exists", form.getMaterialId());
        }
        if (lookup.supplierNames(List.of(form.getSupplierId())).isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.supplier-not-exists", form.getSupplierId());
        }
        MrpBom bom = bomService.findLatestActive(form.getMaterialId());
        if (bom == null) {
            throw BusinessException.ofKey("erp.mrp.no-valid-bom-no-subcontract", materials.get(form.getMaterialId()).getName());
        }
        return bom;
    }

    private void applyForm(MrpOutsource os, OutsourceForm form) {
        os.setSupplierId(form.getSupplierId());
        os.setMaterialId(form.getMaterialId());
        os.setQty(form.getQty());
        os.setIssueWarehouseId(form.getIssueWarehouseId());
        os.setInWarehouseId(form.getInWarehouseId());
        os.setFeeUnitPrice(form.getFeeUnitPrice());
        os.setRemark(form.getRemark());
    }

    /** 发料明细 = BOM子件用量 × 委外数量 × (1+损耗率/100) */
    private void insertItems(Long outsourceId, Long bomId, BigDecimal qty) {
        List<MrpBomItem> bomItems = bomItemMapper.selectList(
                Wrappers.lambdaQuery(MrpBomItem.class).eq(MrpBomItem::getBomId, bomId));
        for (MrpBomItem item : bomItems) {
            BigDecimal lossRate = item.getLossRate() == null ? BigDecimal.ZERO : item.getLossRate();
            BigDecimal issueQty = item.getQty().multiply(qty)
                    .multiply(BigDecimal.ONE.add(lossRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(6, RoundingMode.HALF_UP);
            MrpOutsourceItem oi = new MrpOutsourceItem();
            oi.setOutsourceId(outsourceId);
            oi.setMaterialId(item.getMaterialId());
            oi.setQty(issueQty);
            outsourceItemMapper.insert(oi);
        }
    }

    private MrpOutsource requireOrder(Long id) {
        MrpOutsource os = outsourceMapper.selectById(id);
        if (os == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.subcontract-not-exists", id);
        }
        return os;
    }

    private void requireDraft(MrpOutsource os) {
        if (!MrpConstants.OS_DRAFT.equals(os.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.subcontract-only-draft", os.getStatus());
        }
    }

    private List<MrpOutsourceItem> listItems(Long outsourceId) {
        return outsourceItemMapper.selectList(Wrappers.lambdaQuery(MrpOutsourceItem.class)
                .eq(MrpOutsourceItem::getOutsourceId, outsourceId)
                .orderByAsc(MrpOutsourceItem::getId));
    }

    private OutsourceVO toVO(MrpOutsource os, Map<Long, MaterialBriefVO> materials,
                             Map<Long, String> supplierNames, List<OutsourceItemVO> items) {
        OutsourceVO vo = new OutsourceVO();
        vo.setId(os.getId());
        vo.setOrderNo(os.getOrderNo());
        vo.setSupplierId(os.getSupplierId());
        vo.setSupplierName(supplierNames.get(os.getSupplierId()));
        vo.setMaterialId(os.getMaterialId());
        MaterialBriefVO m = materials.get(os.getMaterialId());
        if (m != null) {
            vo.setMaterialCode(m.getCode());
            vo.setMaterialName(m.getName());
        }
        vo.setQty(os.getQty());
        vo.setBomId(os.getBomId());
        vo.setBomVersion(os.getBomVersion());
        vo.setIssueWarehouseId(os.getIssueWarehouseId());
        vo.setInWarehouseId(os.getInWarehouseId());
        vo.setFeeUnitPrice(os.getFeeUnitPrice());
        vo.setStatus(os.getStatus());
        vo.setRemark(os.getRemark());
        vo.setCreateTime(os.getCreateTime());
        vo.setItems(items);
        return vo;
    }
}
