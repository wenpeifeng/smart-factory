package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.cost.service.CostService;
import com.erp.framework.security.SecurityUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.service.StockService;
import com.erp.purchase.dto.form.PurReturnForm;
import com.erp.purchase.dto.query.PurReturnQuery;
import com.erp.purchase.dto.vo.PurReturnVO;
import com.erp.purchase.dto.vo.ReturnPreviewVO;
import com.erp.purchase.entity.PurInstock;
import com.erp.purchase.entity.PurInstockDetail;
import com.erp.purchase.entity.PurReturn;
import com.erp.purchase.entity.PurReturnDetail;
import com.erp.purchase.mapper.BasedataMapper;
import com.erp.purchase.mapper.PurInstockDetailMapper;
import com.erp.purchase.mapper.PurInstockMapper;
import com.erp.purchase.mapper.PurOrderDetailMapper;
import com.erp.purchase.mapper.PurReturnDetailMapper;
import com.erp.purchase.mapper.PurReturnMapper;
import com.erp.purchase.service.OrderNoGenerator;
import com.erp.purchase.service.PurOrderService;
import com.erp.purchase.service.PurReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购退货单 Service 实现。
 * <p>
 * 审核（三模块集成接线后）事务顺序（任一步失败整体回滚）：
 * 状态推进(条件UPDATE，audit_by=当前登录用户) -> 校验来源入库单已审核
 * -> 每明细经 {@link StockService#stockOut} 反向出库（批次策略：入库明细带批次 -> BATCH
 * 从原批次退回；否则 FIFO/汇总行，库存不足抛异常回滚，禁止负库存语义不变）
 * -> 同事务调 {@link CostService#onStockOut} 按移动加权价冲减（PUR_RET）
 * -> 回写入库明细已退数量、订单明细累计入库数（负向）-> 刷新订单进度（允许30->20回落）。
 * 退货不写 fin_payable（负数红字方案待与 finance 模块联调确定，见 docs/modules/purchase.md）。
 */
@Service
@RequiredArgsConstructor
public class PurReturnServiceImpl implements PurReturnService {

    /** 成本模块约定的业务类型编码（与 inventory InvBizType.PUR_RET 一致） */
    private static final String COST_BIZ_PUR_RET = "PUR_RET";

    private final PurReturnMapper returnMapper;
    private final PurReturnDetailMapper returnDetailMapper;
    private final PurInstockMapper instockMapper;
    private final PurInstockDetailMapper instockDetailMapper;
    private final PurOrderDetailMapper orderDetailMapper;
    private final BasedataMapper basedataMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final PurOrderService purOrderService;
    /** 库存唯一入口（批次感知，替代一期裸 SQL 操作 inv_stock/inv_movement） */
    private final StockService stockService;
    /** 计价唯一入口（同事务实时冲减退货成本） */
    private final CostService costService;

    @Override
    public PageResult<PurReturnVO> page(PurReturnQuery query) {
        var page = returnMapper.selectReturnPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PurReturnVO detail(Long id) {
        PurReturn ret = requireReturn(id);
        PurReturnVO vo = new PurReturnVO();
        vo.setId(ret.getId());
        vo.setReturnNo(ret.getReturnNo());
        vo.setInstockId(ret.getInstockId());
        PurInstock instock = instockMapper.selectById(ret.getInstockId());
        vo.setInstockNo(instock == null ? null : instock.getInstockNo());
        vo.setSupplierId(ret.getSupplierId());
        vo.setSupplierName(basedataMapper.selectSupplierName(ret.getSupplierId()));
        vo.setWarehouseId(ret.getWarehouseId());
        vo.setWarehouseName(basedataMapper.selectWarehouseName(ret.getWarehouseId()));
        vo.setReturnDate(ret.getReturnDate());
        vo.setTotalAmount(ret.getTotalAmount());
        vo.setStatus(ret.getStatus());
        vo.setAuditTime(ret.getAuditTime());
        vo.setRemark(ret.getRemark());
        vo.setCreateTime(ret.getCreateTime());
        vo.setDetails(returnDetailMapper.selectDetailsWithMaterial(id));
        return vo;
    }

    @Override
    public ReturnPreviewVO previewFromInstock(Long instockId) {
        PurInstock instock = requireAuditedInstock(instockId);
        ReturnPreviewVO vo = new ReturnPreviewVO();
        vo.setInstockId(instock.getId());
        vo.setInstockNo(instock.getInstockNo());
        vo.setSupplierId(instock.getSupplierId());
        vo.setSupplierName(basedataMapper.selectSupplierName(instock.getSupplierId()));
        vo.setWarehouseId(instock.getWarehouseId());
        vo.setWarehouseName(basedataMapper.selectWarehouseName(instock.getWarehouseId()));
        vo.setInstockDate(instock.getInstockDate());
        // 只带出还有剩余可退数量的明细行
        List<ReturnPreviewVO.ReturnPreviewDetailVO> lines =
                instockDetailMapper.selectDetailsWithMaterial(instockId).stream()
                        .filter(d -> d.getQty().subtract(d.getReturnedQty()).signum() > 0)
                        .map(d -> {
                            ReturnPreviewVO.ReturnPreviewDetailVO p = new ReturnPreviewVO.ReturnPreviewDetailVO();
                            p.setInstockDetailId(d.getId());
                            p.setMaterialId(d.getMaterialId());
                            p.setMaterialCode(d.getMaterialCode());
                            p.setMaterialName(d.getMaterialName());
                            p.setSpec(d.getSpec());
                            p.setUnit(d.getUnit());
                            p.setPrice(d.getPrice());
                            p.setTaxRate(d.getTaxRate());
                            p.setInstockQty(d.getQty());
                            p.setReturnedQty(d.getReturnedQty());
                            p.setSuggestQty(d.getQty().subtract(d.getReturnedQty()));
                            return p;
                        })
                        .toList();
        vo.setDetails(lines);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurReturnForm form) {
        PurInstock instock = validateForm(form);
        List<PurReturnDetail> details = buildDetails(form, instock, 0L);
        PurReturn ret = new PurReturn();
        ret.setReturnNo(orderNoGenerator.next(OrderNoGenerator.PREFIX_PUR_RETURN));
        ret.setInstockId(form.getInstockId());
        ret.setSupplierId(form.getSupplierId());
        ret.setWarehouseId(form.getWarehouseId());
        ret.setReturnDate(form.getReturnDate());
        ret.setStatus(PurReturn.STATUS_DRAFT);
        ret.setRemark(form.getRemark());
        ret.setTotalAmount(DocCalc.total(details.stream().map(PurReturnDetail::getAmount).toList()));
        returnMapper.insert(ret);
        details.forEach(d -> {
            d.setReturnId(ret.getId());
            returnDetailMapper.insert(d);
        });
        return ret.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurReturnForm form) {
        PurReturn ret = requireReturn(id);
        if (ret.getStatus() != PurReturn.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.return-only-draft-modify");
        }
        PurInstock instock = validateForm(form);
        // 修改时排除自身占用的草稿在途退货数量
        List<PurReturnDetail> details = buildDetails(form, instock, id);
        returnDetailMapper.delete(Wrappers.lambdaQuery(PurReturnDetail.class)
                .eq(PurReturnDetail::getReturnId, id));
        ret.setSupplierId(form.getSupplierId());
        ret.setWarehouseId(form.getWarehouseId());
        ret.setReturnDate(form.getReturnDate());
        ret.setRemark(form.getRemark());
        ret.setTotalAmount(DocCalc.total(details.stream().map(PurReturnDetail::getAmount).toList()));
        returnMapper.updateById(ret);
        details.forEach(d -> {
            d.setReturnId(id);
            returnDetailMapper.insert(d);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        // 1) 状态推进（条件 UPDATE 防并发重复审核）
        int rows = returnMapper.audit(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            requireReturn(id);
            throw BusinessException.ofKey("erp.pur.return-only-draft-audit");
        }
        PurReturn ret = requireReturn(id);
        // 2) 防御：来源入库单必须已审核（其库存/应付已落账，退货才有冲减依据）
        PurInstock instock = requireAuditedInstock(ret.getInstockId());

        List<PurReturnDetail> details = returnDetailMapper.selectList(
                Wrappers.lambdaQuery(PurReturnDetail.class).eq(PurReturnDetail::getReturnId, id));
        // 3) 每明细：StockService 反向出库（原入库批次 BATCH 优先，否则 FIFO/汇总行；
        //    库存不足由 StockService 抛 BusinessException，整个审核事务回滚）
        //    + CostService 按移动加权价实时冲减 + 回写入库明细已退数、订单累计入库数
        for (PurReturnDetail d : details) {
            // 找到入库明细（取其批次决定退货出库策略，同时供订单累计冲减）
            PurInstockDetail instockDetail = instockDetailMapper.selectById(d.getInstockDetailId());
            if (instockDetail == null) {
                throw BusinessException.ofKey("erp.pur.instock-line-not-exists", d.getInstockDetailId());
            }
            String srcBatch = instockDetail.getBatchNo();
            if (srcBatch != null && !srcBatch.isBlank()) {
                // 原入库落在指定批次：从原批次退回（不足=该批次被消耗，抛异常回滚）
                stockService.stockOut(ret.getWarehouseId(), d.getMaterialId(), d.getQty(),
                        OutStrategy.BATCH, srcBatch, InvBizType.PUR_RET, ret.getId(), ret.getReturnNo(),
                        "采购退货 " + ret.getReturnNo());
            } else {
                // 非批次物料走汇总行原逻辑；批次物料的历史入库明细无批次时按 FIFO 兜底
                stockService.stockOut(ret.getWarehouseId(), d.getMaterialId(), d.getQty(),
                        OutStrategy.FIFO, null, InvBizType.PUR_RET, ret.getId(), ret.getReturnNo(),
                        "采购退货 " + ret.getReturnNo());
            }
            // 退货成本按当前移动加权价冲减（返回值即出库成本）
            costService.onStockOut(d.getMaterialId(), ret.getWarehouseId(), COST_BIZ_PUR_RET,
                    ret.getReturnNo(), ret.getId(), d.getQty(), ret.getReturnDate());

            if (instockDetailMapper.addReturnedQtyGuarded(d.getInstockDetailId(), d.getQty()) == 0) {
                throw BusinessException.ofKey("erp.pur.return-over-inbound-qty", d.getInstockDetailId());
            }
            // 负向冲减订单累计入库数量
            if (orderDetailMapper.addReceivedQty(instockDetail.getOrderDetailId(), d.getQty().negate()) == 0) {
                throw BusinessException.ofKey("erp.pur.po-line-not-exists-return", instockDetail.getOrderDetailId());
            }
        }
        // 4) 刷新订单进度（全部入库不再成立时 30已完成 回落 20部分入库）
        purOrderService.refreshProgressStatus(instock.getOrderId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurReturn ret = requireReturn(id);
        if (ret.getStatus() != PurReturn.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.return-only-draft-delete");
        }
        returnMapper.deleteById(id);
        returnDetailMapper.delete(Wrappers.lambdaQuery(PurReturnDetail.class)
                .eq(PurReturnDetail::getReturnId, id));
    }

    /** 加载退货单，不存在抛业务异常 */
    private PurReturn requireReturn(Long id) {
        PurReturn ret = returnMapper.selectById(id);
        if (ret == null) {
            throw BusinessException.ofKey("erp.pur.return-not-exists", id);
        }
        return ret;
    }

    /** 加载已审核状态的来源入库单，否则抛业务异常 */
    private PurInstock requireAuditedInstock(Long instockId) {
        PurInstock instock = instockMapper.selectById(instockId);
        if (instock == null) {
            throw BusinessException.ofKey("erp.pur.source-instock-not-exists", instockId);
        }
        if (instock.getStatus() != PurInstock.STATUS_AUDITED) {
            throw BusinessException.ofKey("erp.pur.instock-not-audited-no-return", instock.getInstockNo());
        }
        return instock;
    }

    /** 表单基础校验：入库单已审核、供应商/仓库与入库单一致 */
    private PurInstock validateForm(PurReturnForm form) {
        PurInstock instock = requireAuditedInstock(form.getInstockId());
        if (!form.getSupplierId().equals(instock.getSupplierId())
                || !form.getWarehouseId().equals(instock.getWarehouseId())) {
            throw BusinessException.ofKey("erp.pur.return-source-consistency");
        }
        return instock;
    }

    /**
     * 构建退货明细：校验来源明细归属入库单、物料一致、退货数量<=可退数量。
     * 可退数量 = 入库数量 - 累计已退 - 其他草稿退货单在途数量
     * （returned_qty 只在审核时回写，若不扣草稿在途，多张草稿会累计超退）。
     *
     * @param excludeReturnId 修改场景排除自身的退货单 ID，新增传 0
     */
    private List<PurReturnDetail> buildDetails(PurReturnForm form, PurInstock instock, Long excludeReturnId) {
        return form.getDetails().stream()
                .map(line -> {
                    PurInstockDetail instockDetail = instockDetailMapper.selectById(line.getInstockDetailId());
                    if (instockDetail == null || !instockDetail.getInstockId().equals(instock.getId())) {
                        throw BusinessException.ofKey("erp.pur.instock-line-not-in-order", line.getInstockDetailId(), instock.getInstockNo());
                    }
                    if (!instockDetail.getMaterialId().equals(line.getMaterialId())) {
                        throw BusinessException.ofKey("erp.pur.return-material-mismatch", line.getInstockDetailId());
                    }
                    BigDecimal pending = returnDetailMapper.sumDraftPendingQty(
                            line.getInstockDetailId(), excludeReturnId);
                    BigDecimal returnable = instockDetail.getQty()
                            .subtract(instockDetail.getReturnedQty())
                            .subtract(pending);
                    if (line.getQty().compareTo(returnable) > 0) {
                        throw BusinessException.ofKey("erp.pur.return-over-returnable", line.getMaterialId(), line.getQty().toPlainString(), returnable.toPlainString());
                    }
                    PurReturnDetail d = new PurReturnDetail();
                    d.setInstockDetailId(line.getInstockDetailId());
                    d.setMaterialId(line.getMaterialId());
                    d.setQty(line.getQty());
                    d.setPrice(line.getPrice());
                    d.setTaxRate(line.getTaxRate() == null ? BigDecimal.ZERO : line.getTaxRate());
                    d.setAmount(DocCalc.lineAmount(line.getQty(), line.getPrice()));
                    d.setRemark(line.getRemark());
                    return d;
                })
                .toList();
    }
}
