package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.cost.service.CostService;
import com.erp.framework.security.SecurityUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.service.StockService;
import com.erp.purchase.dto.form.PurInstockForm;
import com.erp.purchase.dto.query.PurInstockQuery;
import com.erp.purchase.dto.vo.InstockPreviewVO;
import com.erp.purchase.dto.vo.PurInstockVO;
import com.erp.purchase.entity.FinPayable;
import com.erp.purchase.entity.PurInstock;
import com.erp.purchase.entity.PurInstockDetail;
import com.erp.purchase.entity.PurOrder;
import com.erp.purchase.entity.PurOrderDetail;
import com.erp.purchase.event.PurInstockAuditedEvent;
import com.erp.purchase.mapper.BasedataMapper;
import com.erp.purchase.mapper.PurFinPayableMapper;
import com.erp.purchase.mapper.PurInstockDetailMapper;
import com.erp.purchase.mapper.PurInstockMapper;
import com.erp.purchase.mapper.PurOrderDetailMapper;
import com.erp.purchase.mapper.PurOrderMapper;
import com.erp.purchase.service.OrderNoGenerator;
import com.erp.purchase.service.PurInstockService;
import com.erp.purchase.service.PurOrderService;
import com.erp.qc.dto.vo.InspectPendingVO;
import com.erp.qc.service.QcInspectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 采购入库单 Service 实现。
 * <p>
 * 审核（三模块集成接线后）事务顺序（任一步失败整体回滚）：
 * <ol>
 *   <li>QC 卡点：来源单存在草稿检验单（{@code QcInspectService#pendingOfSource}）
 *       则拒绝审核；无检验单放行（免检直过）；</li>
 *   <li>状态推进（条件 UPDATE 防并发重复审核，audit_by=当前登录用户）；</li>
 *   <li>每明细经 {@link StockService#stockIn} 过账库存（批次感知：表单批次号透传，
 *       空且非批次物料落汇总行，批次物料缺批次号由 StockService 强校验拒绝）；</li>
 *   <li>同事务调 {@link CostService#onStockIn} 实时计价（采购金额=明细含税金额，
 *       与应付口径一致，cost_flow 即时产生流水）；</li>
 *   <li>回写订单明细累计入库数 -> INSERT fin_payable -> 刷新订单进度状态。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurInstockServiceImpl implements PurInstockService {

    /** QC 挂点约定的来源单据类型（与 erp-qc QcInspect.SOURCE_PUR_INSTOCK 常量一致） */
    private static final String QC_SOURCE_PUR_INSTOCK = "pur_instock";

    /** 成本模块约定的业务类型编码（与 inventory InvBizType.PUR_IN 一致，字符串传入不依赖对方枚举） */
    private static final String COST_BIZ_PUR_IN = "PUR_IN";

    private final PurInstockMapper instockMapper;
    private final PurInstockDetailMapper instockDetailMapper;
    private final PurOrderMapper orderMapper;
    private final PurOrderDetailMapper orderDetailMapper;
    private final BasedataMapper basedataMapper;
    private final PurFinPayableMapper finPayableMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final PurOrderService purOrderService;
    /** 库存唯一入口（批次感知，替代一期裸 SQL 操作 inv_stock/inv_movement） */
    private final StockService stockService;
    /** 计价唯一入口（同事务实时归集采购成本） */
    private final CostService costService;
    /** 质检卡点（存在未完成检验单时拒绝入库审核） */
    private final QcInspectService qcInspectService;
    /** 事件发布器（V231 暂估挂点：入库审核成功事件，暂估服务 AFTER_COMMIT 消费） */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<PurInstockVO> page(PurInstockQuery query) {
        var page = instockMapper.selectInstockPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PurInstockVO detail(Long id) {
        PurInstock instock = requireInstock(id);
        PurInstockVO vo = new PurInstockVO();
        vo.setId(instock.getId());
        vo.setInstockNo(instock.getInstockNo());
        vo.setOrderId(instock.getOrderId());
        PurOrder order = orderMapper.selectById(instock.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());
        vo.setSupplierId(instock.getSupplierId());
        vo.setSupplierName(basedataMapper.selectSupplierName(instock.getSupplierId()));
        vo.setWarehouseId(instock.getWarehouseId());
        vo.setWarehouseName(basedataMapper.selectWarehouseName(instock.getWarehouseId()));
        vo.setInstockDate(instock.getInstockDate());
        vo.setTotalAmount(instock.getTotalAmount());
        vo.setStatus(instock.getStatus());
        vo.setAuditTime(instock.getAuditTime());
        vo.setRemark(instock.getRemark());
        vo.setCreateTime(instock.getCreateTime());
        vo.setDetails(instockDetailMapper.selectDetailsWithMaterial(id));
        return vo;
    }

    @Override
    public InstockPreviewVO previewFromOrder(Long orderId) {
        PurOrder order = requireOrderForInstock(orderId);
        InstockPreviewVO vo = new InstockPreviewVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setSupplierId(order.getSupplierId());
        vo.setSupplierName(basedataMapper.selectSupplierName(order.getSupplierId()));
        vo.setWarehouseId(order.getWarehouseId());
        vo.setWarehouseName(order.getWarehouseId() == null
                ? null : basedataMapper.selectWarehouseName(order.getWarehouseId()));
        vo.setOrderDate(order.getOrderDate());
        // 只带出还有剩余可入库数量的明细行
        List<InstockPreviewVO.InstockPreviewDetailVO> lines =
                orderDetailMapper.selectDetailsWithMaterial(orderId).stream()
                        .filter(d -> d.getQty().subtract(d.getReceivedQty()).signum() > 0)
                        .map(d -> {
                            InstockPreviewVO.InstockPreviewDetailVO p = new InstockPreviewVO.InstockPreviewDetailVO();
                            p.setOrderDetailId(d.getId());
                            p.setMaterialId(d.getMaterialId());
                            p.setMaterialCode(d.getMaterialCode());
                            p.setMaterialName(d.getMaterialName());
                            p.setSpec(d.getSpec());
                            p.setUnit(d.getUnit());
                            p.setPrice(d.getPrice());
                            p.setTaxRate(d.getTaxRate());
                            p.setOrderQty(d.getQty());
                            p.setReceivedQty(d.getReceivedQty());
                            p.setSuggestQty(d.getQty().subtract(d.getReceivedQty()));
                            p.setDeliveryDate(d.getDeliveryDate());
                            return p;
                        })
                        .toList();
        vo.setDetails(lines);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurInstockForm form) {
        PurOrder order = validateForm(form);
        List<PurInstockDetail> details = buildDetails(form, order, 0L);
        PurInstock instock = new PurInstock();
        instock.setInstockNo(orderNoGenerator.next(OrderNoGenerator.PREFIX_PUR_INSTOCK));
        instock.setOrderId(order.getId());
        instock.setSupplierId(form.getSupplierId());
        instock.setWarehouseId(form.getWarehouseId());
        instock.setInstockDate(form.getInstockDate());
        instock.setStatus(PurInstock.STATUS_DRAFT);
        instock.setRemark(form.getRemark());
        instock.setTotalAmount(DocCalc.total(details.stream().map(PurInstockDetail::getAmount).toList()));
        instockMapper.insert(instock);
        details.forEach(d -> {
            d.setInstockId(instock.getId());
            instockDetailMapper.insert(d);
        });
        return instock.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurInstockForm form) {
        PurInstock instock = requireInstock(id);
        if (instock.getStatus() != PurInstock.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.instock-only-draft-modify");
        }
        PurOrder order = validateForm(form);
        // 修改时排除自身占用的草稿在途数量
        List<PurInstockDetail> details = buildDetails(form, order, id);
        instockDetailMapper.delete(Wrappers.lambdaQuery(PurInstockDetail.class)
                .eq(PurInstockDetail::getInstockId, id));
        instock.setSupplierId(form.getSupplierId());
        instock.setWarehouseId(form.getWarehouseId());
        instock.setInstockDate(form.getInstockDate());
        instock.setRemark(form.getRemark());
        instock.setTotalAmount(DocCalc.total(details.stream().map(PurInstockDetail::getAmount).toList()));
        instockMapper.updateById(instock);
        details.forEach(d -> {
            d.setInstockId(id);
            instockDetailMapper.insert(d);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        // 审计日志：入库审核一旦成功即写库存/成本/应付三本账，事后争议追溯必须知道
        // 是谁在何时审了哪张单（操作人另由 MDC userId 随每行日志携带）
        log.info("[采购入库][审核] 开始 单号id={} 操作人={}", id, SecurityUtils.getCurrentUserId());
        // 0) 质检卡点（先于状态推进，拒绝时不留任何痕迹）：存在草稿（未完成）检验单即拒绝；
        //    无检验单放行（免检直过，符合中小企业实际）。先调 QC 的来源单存在性校验也顺带
        //    验证了入库单本身存在。
        InspectPendingVO pending = qcInspectService.pendingOfSource(QC_SOURCE_PUR_INSTOCK, id);
        if (pending.isPending()) {
            String nos = pending.getPendingList().stream()
                    .map(p -> p.getInspectNo())
                    .collect(Collectors.joining("、"));
            // warn 而非 info：质检未完成导致的审核拒绝是过程异常，需在日志里一眼可辨
            log.warn("[采购入库][审核] 拒绝 单号id={} 原因=存在未完成检验单({})", id, nos);
            throw BusinessException.ofKey("erp.pur.unfinished-inspection-no-audit", nos);
        }
        // 1) 状态推进（条件 UPDATE 防并发重复审核；0 行=非草稿直接失败，尚未写任何库存数据）
        int rows = instockMapper.audit(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            requireInstock(id);
            log.warn("[采购入库][审核] 拒绝 单号id={} 原因=非草稿状态（可能并发已审核）", id);
            throw BusinessException.ofKey("erp.pur.instock-only-draft-audit");
        }
        PurInstock instock = requireInstock(id);
        // 2) 防御：订单在入库单创建后被关闭/删除的场景，禁止库存与应付落账
        PurOrder order = requireOrderForInstock(instock.getOrderId());

        List<PurInstockDetail> details = instockDetailMapper.selectList(
                Wrappers.lambdaQuery(PurInstockDetail.class).eq(PurInstockDetail::getInstockId, id));
        // 3) 每明细：StockService 批次入库（inv_stock UPSERT + inv_movement 同事务）
        //    + CostService 实时计价（PUR_IN 带采购金额）+ 订单累计入库回写
        for (PurInstockDetail d : details) {
            stockService.stockIn(instock.getWarehouseId(), d.getMaterialId(), d.getBatchNo(),
                    d.getQty(), InvBizType.PUR_IN, instock.getId(), instock.getInstockNo(),
                    "采购入库 " + instock.getInstockNo());
            // 采购金额=明细含税金额（与应付口径一致）；期间按入库日期归属
            costService.onStockIn(d.getMaterialId(), instock.getWarehouseId(), COST_BIZ_PUR_IN,
                    instock.getInstockNo(), d.getId(), d.getQty(), d.getAmount(), instock.getInstockDate());

            int updated = orderDetailMapper.addReceivedQtyGuarded(d.getOrderDetailId(), d.getQty());
            if (updated == 0) {
                // warn：超收被守卫拦截（多张入库单并发占用量），业务单据与订单数量对不上的排查入口
                log.warn("[采购入库][审核] 拒绝 单号={} 订单明细id={} 原因=本次入库将超订单数量，事务回滚",
                        instock.getInstockNo(), d.getOrderDetailId());
                throw BusinessException.ofKey("erp.pur.inbound-over-order-qty", d.getOrderDetailId());
            }
        }
        // 4) 生成应付（约定表 INSERT，结算归 finance 模块）
        FinPayable payable = new FinPayable();
        payable.setSupplierId(instock.getSupplierId());
        payable.setBizNo(instock.getInstockNo());
        payable.setAmount(instock.getTotalAmount());
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setStatus(FinPayable.STATUS_UNSETTLED);
        finPayableMapper.insert(payable);
        // 5) 刷新订单进度：全部入库->已完成，否则->部分入库
        purOrderService.refreshProgressStatus(order.getId());
        // 6) 暂估挂点（V231）：事务提交后由暂估服务消费（无生效发票时自动登记暂估应付，
        //    失败不影响本事务——AFTER_COMMIT + 自管事务，漏单由扫描器兜底）
        eventPublisher.publishEvent(new PurInstockAuditedEvent(instock.getId(), instock.getInstockNo()));
        // 审计日志：完成行带单号/数量/金额三要素，与库存、成本链路日志靠同一 traceId 串联
        BigDecimal totalQty = details.stream().map(PurInstockDetail::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("[采购入库][审核] 完成 单号={} 订单={} 明细数={} 总数量={} 总金额={} 应付单已生成 操作人={}",
                instock.getInstockNo(), order.getOrderNo(), details.size(),
                totalQty.stripTrailingZeros().toPlainString(),
                instock.getTotalAmount().toPlainString(), SecurityUtils.getCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurInstock instock = requireInstock(id);
        if (instock.getStatus() != PurInstock.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.instock-only-draft-delete");
        }
        instockMapper.deleteById(id);
        instockDetailMapper.delete(Wrappers.lambdaQuery(PurInstockDetail.class)
                .eq(PurInstockDetail::getInstockId, id));
    }

    /** 加载入库单，不存在抛业务异常 */
    private PurInstock requireInstock(Long id) {
        PurInstock instock = instockMapper.selectById(id);
        if (instock == null) {
            throw BusinessException.ofKey("erp.pur.instock-not-exists", id);
        }
        return instock;
    }

    /** 加载可入库状态的订单（已审核/部分入库），否则抛业务异常 */
    private PurOrder requireOrderForInstock(Long orderId) {
        PurOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.ofKey("erp.pur.source-po-not-exists", orderId);
        }
        if (!PurOrderServiceImpl.canInstock(order)) {
            throw BusinessException.ofKey("erp.pur.po-status-no-inbound", order.getOrderNo(), order.getStatus());
        }
        return order;
    }

    /**
     * 表单基础校验：订单可入库、供应商与订单一致、仓库在用。
     */
    private PurOrder validateForm(PurInstockForm form) {
        PurOrder order = requireOrderForInstock(form.getOrderId());
        if (!form.getSupplierId().equals(order.getSupplierId())) {
            throw BusinessException.ofKey("erp.pur.supplier-mismatch-no-inbound");
        }
        if (basedataMapper.countWarehouseById(form.getWarehouseId()) == 0) {
            throw BusinessException.ofKey("erp.pur.in-warehouse-not-exists", form.getWarehouseId());
        }
        return order;
    }

    /**
     * 构建入库明细：校验来源明细归属订单、物料一致、实收数量>0 且不超收。
     * 可入库数量 = 订单数量 - 累计已入库 - 其他草稿入库单在途数量
     * （received_qty 只在审核时回写，若不扣草稿在途，多张草稿会累计超收）。
     *
     * @param excludeInstockId 修改场景排除自身的入库单 ID，新增传 0
     */
    private List<PurInstockDetail> buildDetails(PurInstockForm form, PurOrder order, Long excludeInstockId) {
        return form.getDetails().stream()
                .map(line -> {
                    PurOrderDetail od = orderDetailMapper.selectById(line.getOrderDetailId());
                    if (od == null || !od.getOrderId().equals(order.getId())) {
                        throw BusinessException.ofKey("erp.pur.po-line-not-in-order", line.getOrderDetailId(), order.getOrderNo());
                    }
                    if (!od.getMaterialId().equals(line.getMaterialId())) {
                        throw BusinessException.ofKey("erp.pur.inbound-material-mismatch", line.getOrderDetailId());
                    }
                    BigDecimal pending = instockDetailMapper.sumDraftPendingQty(
                            line.getOrderDetailId(), excludeInstockId);
                    BigDecimal remain = od.getQty().subtract(od.getReceivedQty()).subtract(pending);
                    if (line.getQty().compareTo(remain) > 0) {
                        throw BusinessException.ofKey("erp.pur.received-over-remaining", line.getMaterialId(), line.getQty().toPlainString(), remain.toPlainString());
                    }
                    PurInstockDetail d = new PurInstockDetail();
                    d.setOrderDetailId(line.getOrderDetailId());
                    d.setMaterialId(line.getMaterialId());
                    d.setBatchNo(line.getBatchNo() == null ? "" : line.getBatchNo().trim());
                    d.setQty(line.getQty());
                    d.setPrice(line.getPrice());
                    d.setTaxRate(line.getTaxRate() == null ? BigDecimal.ZERO : line.getTaxRate());
                    d.setAmount(DocCalc.lineAmount(line.getQty(), line.getPrice()));
                    d.setReturnedQty(BigDecimal.ZERO);
                    d.setRemark(line.getRemark());
                    return d;
                })
                .toList();
    }
}
