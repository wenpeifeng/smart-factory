package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.ToPurchaseBatchForm;
import com.erp.mrp.dto.form.ToPurchaseForm;
import com.erp.mrp.dto.form.ToWorkOrderForm;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.SuggestionVO;
import com.erp.mrp.entity.MrpSuggestion;
import com.erp.mrp.mapper.MrpSuggestionMapper;
import com.erp.mrp.service.MrpSuggestionService;
import com.erp.mrp.service.MrpWorkOrderService;
import com.erp.purchase.dto.form.PurOrderForm;
import com.erp.purchase.service.PurOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * MRP 建议服务实现。
 * <p>
 * 转采购接线（mrp → purchase，依赖方向单向、purchase 不依赖 mrp）：
 * PURCHASE+PENDING 建议组装采购订单草稿调 {@link PurOrderService#create}——
 * 供应商可空（草稿待补填，采购侧审核前强校验）、单价 0 待采购员补填、
 * 要求交货日期=建议日期、备注记「MRP 生成，批次号+建议ID」；成功后建议
 * 状态→CONVERTED 并回写 refType=PUR_ORDER / refId=采购订单ID。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrpSuggestionServiceImpl implements MrpSuggestionService {

    private final MrpSuggestionMapper suggestionMapper;
    private final MrpWorkOrderService workOrderService;
    private final MrpLookupHelper lookup;
    private final PurOrderService purOrderService;

    @Override
    public PageResult<SuggestionVO> page(long pageNum, long pageSize, String batchNo,
                                         String planType, String status) {
        PageResult<MrpSuggestion> page = PageUtils.of(suggestionMapper,
                Wrappers.lambdaQuery(MrpSuggestion.class)
                        .eq(notBlank(batchNo), MrpSuggestion::getBatchNo, batchNo)
                        .eq(notBlank(planType), MrpSuggestion::getPlanType, planType)
                        .eq(notBlank(status), MrpSuggestion::getStatus, status)
                        .orderByDesc(MrpSuggestion::getId),
                pageNum, pageSize);
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                page.getRecords().stream().map(MrpSuggestion::getMaterialId).toList());
        return page.map(sug -> {
            SuggestionVO vo = new SuggestionVO();
            vo.setId(sug.getId());
            vo.setBatchNo(sug.getBatchNo());
            vo.setPlanType(sug.getPlanType());
            vo.setMaterialId(sug.getMaterialId());
            MaterialBriefVO m = materials.get(sug.getMaterialId());
            if (m != null) {
                vo.setMaterialCode(m.getCode());
                vo.setMaterialName(m.getName());
                vo.setUnit(m.getUnit());
            }
            vo.setQty(sug.getQty());
            vo.setSuggestDate(sug.getSuggestDate());
            vo.setLevel(sug.getLevel());
            vo.setStatus(sug.getStatus());
            vo.setRefType(sug.getRefType());
            vo.setRefId(sug.getRefId());
            vo.setCreateTime(sug.getCreateTime());
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long toWorkOrder(Long id, ToWorkOrderForm form) {
        MrpSuggestion sug = suggestionMapper.selectById(id);
        if (sug == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.suggestion-not-exists", id);
        }
        if (!MrpConstants.PLAN_PRODUCE.equals(sug.getPlanType())) {
            throw BusinessException.ofKey("erp.mrp.only-produce-to-production");
        }
        if (!MrpConstants.SUGGESTION_PENDING.equals(sug.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.suggestion-converted");
        }
        Long workOrderId = workOrderService.createFromSuggestion(sug,
                form == null ? null : form.getWarehouseId());
        // 回写建议：已转单 + 引用生产订单
        MrpSuggestion upd = new MrpSuggestion();
        upd.setId(id);
        upd.setStatus(MrpConstants.SUGGESTION_CONVERTED);
        upd.setRefType(MrpConstants.REF_WORK_ORDER);
        upd.setRefId(workOrderId);
        suggestionMapper.updateById(upd);
        return workOrderId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long toPurchase(Long id, ToPurchaseForm form) {
        MrpSuggestion sug = requirePurchasable(id);
        Long purOrderId = purOrderService.create(buildPurOrderForm(
                form == null ? null : form.getSupplierId(),
                form == null ? null : form.getWarehouseId(),
                "MRP 生成，批次号" + sug.getBatchNo() + "，建议ID " + sug.getId(),
                List.of(sug)));
        markConverted(sug, purOrderId);
        log.info("[MRP转采购] 建议#{}({}) -> 采购订单#{} 供应商={} 数量={}",
                sug.getId(), sug.getBatchNo(), purOrderId,
                form == null ? null : form.getSupplierId(), sug.getQty());
        return purOrderId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long toPurchaseBatch(ToPurchaseBatchForm form) {
        // 去重保序（重复 ID 只转一次），逐条校验 PURCHASE+PENDING
        LinkedHashSet<Long> ids = new LinkedHashSet<>(form.getIds());
        Map<Long, MrpSuggestion> suggestions = new LinkedHashMap<>();
        for (Long id : ids) {
            suggestions.put(id, requirePurchasable(id));
        }
        List<MrpSuggestion> list = List.copyOf(suggestions.values());
        String idStr = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        Long purOrderId = purOrderService.create(buildPurOrderForm(
                form.getSupplierId(), form.getWarehouseId(),
                "MRP 生成，批次号" + list.get(0).getBatchNo() + "，建议ID " + idStr, list));
        for (MrpSuggestion sug : list) {
            markConverted(sug, purOrderId);
        }
        log.info("[MRP批量转采购] 建议ids=[{}] -> 采购订单#{}（{} 行合并一张单） 供应商={}",
                idStr, purOrderId, list.size(), form.getSupplierId());
        return purOrderId;
    }

    /** 建议必须存在、类型 PURCHASE、状态 PENDING（否则拒绝转单） */
    private MrpSuggestion requirePurchasable(Long id) {
        MrpSuggestion sug = suggestionMapper.selectById(id);
        if (sug == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.suggestion-not-exists", id);
        }
        if (!MrpConstants.PLAN_PURCHASE.equals(sug.getPlanType())) {
            throw BusinessException.ofKey("erp.mrp.only-purchase-to-purchase", sug.getPlanType());
        }
        if (!MrpConstants.SUGGESTION_PENDING.equals(sug.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.suggestion-converted-id", id);
        }
        return sug;
    }

    /**
     * 组装采购订单草稿表单：每条建议一行明细——物料/数量取建议，
     * 单价 0（MRP 不感知价格，草稿由采购员补填），要求交货日期=建议日期，
     * 行备注带批次号+建议ID 便于追溯。
     */
    private PurOrderForm buildPurOrderForm(Long supplierId, Long warehouseId,
                                           String orderRemark, List<MrpSuggestion> suggestions) {
        PurOrderForm form = new PurOrderForm();
        form.setSupplierId(supplierId);
        form.setWarehouseId(warehouseId);
        form.setOrderDate(LocalDate.now());
        form.setRemark(orderRemark);
        form.setDetails(suggestions.stream().map(sug -> {
            PurOrderForm.PurOrderDetailForm line = new PurOrderForm.PurOrderDetailForm();
            line.setMaterialId(sug.getMaterialId());
            line.setQty(sug.getQty());
            line.setPrice(BigDecimal.ZERO);
            line.setTaxRate(BigDecimal.ZERO);
            line.setDeliveryDate(sug.getSuggestDate());
            line.setRemark("MRP批次" + sug.getBatchNo() + "·建议#" + sug.getId());
            return line;
        }).toList());
        return form;
    }

    /** 建议回写：CONVERTED + 采购订单引用（同一事务，转单失败建议不动） */
    private void markConverted(MrpSuggestion sug, Long purOrderId) {
        MrpSuggestion upd = new MrpSuggestion();
        upd.setId(sug.getId());
        upd.setStatus(MrpConstants.SUGGESTION_CONVERTED);
        upd.setRefType(MrpConstants.REF_PUR_ORDER);
        upd.setRefId(purOrderId);
        suggestionMapper.updateById(upd);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
