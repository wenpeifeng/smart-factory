package com.erp.report.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.cost.service.CostService;
import com.erp.finance.entity.FinAccount;
import com.erp.finance.entity.FinAccountFlow;
import com.erp.finance.entity.FinPayment;
import com.erp.finance.entity.FinPaymentItem;
import com.erp.finance.entity.FinReceipt;
import com.erp.finance.entity.FinReceiptItem;
import com.erp.finance.mapper.FinAccountFlowMapper;
import com.erp.finance.mapper.FinAccountMapper;
import com.erp.finance.mapper.FinPaymentItemMapper;
import com.erp.finance.mapper.FinPaymentMapper;
import com.erp.finance.mapper.FinReceiptItemMapper;
import com.erp.finance.mapper.FinReceiptMapper;
import com.erp.framework.security.SecurityUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.entity.InvMovement;
import com.erp.inventory.mapper.InvMovementMapper;
import com.erp.inventory.service.StockService;
import com.erp.purchase.entity.PurInstock;
import com.erp.purchase.entity.PurInstockDetail;
import com.erp.purchase.entity.PurReturn;
import com.erp.purchase.entity.PurReturnDetail;
import com.erp.purchase.entity.FinPayable;
import com.erp.purchase.mapper.PurFinPayableMapper;
import com.erp.purchase.mapper.PurInstockDetailMapper;
import com.erp.purchase.mapper.PurInstockMapper;
import com.erp.purchase.mapper.PurReturnDetailMapper;
import com.erp.purchase.mapper.PurReturnMapper;
import com.erp.purchase.service.PurOrderService;
import com.erp.report.constant.DocTypes;
import com.erp.report.dto.vo.ReverseResultVO;
import com.erp.report.entity.BizReverseLog;
import com.erp.report.mapper.BizReverseLogMapper;
import com.erp.report.mapper.DocWriteMapper;
import com.erp.report.service.ReverseService;
import com.erp.sales.entity.SalOutstock;
import com.erp.sales.entity.SalOutstockDetail;
import com.erp.sales.entity.SalReturn;
import com.erp.sales.entity.SalReturnDetail;
import com.erp.sales.mapper.SalOutstockDetailMapper;
import com.erp.sales.mapper.SalOutstockMapper;
import com.erp.sales.mapper.SalReturnDetailMapper;
import com.erp.sales.mapper.SalReturnMapper;
import com.erp.sales.mapper.SalesBizMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用红冲实现（T7）。整体事务顺序（任一步失败整体回滚）：
 * <ol>
 *   <li>校验：类型可红冲、原单存在且 status=10（已审核）、未被红冲过、自身不是红冲单；</li>
 *   <li>生成红冲单（负数金额/数量，单号=原单号+R，status 直接落 10=红冲即过账）；</li>
 *   <li>反向过账：
 *       库存——按原单 inv_movement 流水腿精确反向（同批次同库位一腿一腿冲，
 *       入腿→{@code stockOutAtLocation}、出腿→{@code stockInAt}，库存不足整体回滚）；
 *       成本——与对应退货流同口径（PUR_RET 出 / SAL_RET 入，金额不携带按当前移动加权价）；
 *       应收应付——插入相反符号的 AR/AP 行（与 sal_return 红字应收同口径）；
 *       收付款——核销回冲（settled_amount 守卫扣减）+ 账户余额反向变动 + 账户流水；</li>
 *   <li>数量回退：订单累计入库/出库数、入库/出库明细已退数（全部条件 UPDATE 守卫）；</li>
 *   <li>原单 status 10→20（REVERSED）+ biz_reverse_log 配对留痕。</li>
 * </ol>
 * <p>
 * 注意：原单生成的蓝字应收/应付若已被收付款核销，红冲只插入反向 AR/AP 行对冲，
 * 不自动撤销已有收付款（需人工先红冲对应收付款单，见 docs/modules/reverse-docchain.md）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReverseServiceImpl implements ReverseService {

    private final PurInstockMapper purInstockMapper;
    private final PurInstockDetailMapper purInstockDetailMapper;
    private final PurReturnMapper purReturnMapper;
    private final PurReturnDetailMapper purReturnDetailMapper;
    private final PurFinPayableMapper purPayableMapper;
    private final PurOrderService purOrderService;
    private final SalOutstockMapper salOutstockMapper;
    private final SalOutstockDetailMapper salOutstockDetailMapper;
    private final SalReturnMapper salReturnMapper;
    private final SalReturnDetailMapper salReturnDetailMapper;
    private final SalesBizMapper salesBizMapper;
    private final FinReceiptMapper finReceiptMapper;
    private final FinReceiptItemMapper finReceiptItemMapper;
    private final FinPaymentMapper finPaymentMapper;
    private final FinPaymentItemMapper finPaymentItemMapper;
    private final FinAccountMapper finAccountMapper;
    private final FinAccountFlowMapper finAccountFlowMapper;
    private final InvMovementMapper invMovementMapper;
    private final StockService stockService;
    private final CostService costService;
    private final BizReverseLogMapper reverseLogMapper;
    private final DocWriteMapper docWriteMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReverseResultVO reverse(String docType, Long id, String reason) {
        if (!DocTypes.REVERSIBLE.contains(docType)) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.not-support", docType);
        }
        Long operator = SecurityUtils.getCurrentUserId();
        log.info("[红冲][开始] 类型={} 单据id={} 操作人={} 原因={}", docType, id, operator, reason);
        guardNotReversed(docType, id);

        ReverseResultVO result = switch (docType) {
            case DocTypes.PUR_INSTOCK -> reversePurInstock(id, operator);
            case DocTypes.SAL_OUTSTOCK -> reverseSalOutstock(id, operator);
            case DocTypes.PUR_RETURN -> reversePurReturn(id, operator);
            case DocTypes.SAL_RETURN -> reverseSalReturn(id, operator);
            case DocTypes.RECEIPT -> reverseReceipt(id, operator);
            case DocTypes.PAYMENT -> reversePayment(id, operator);
            default -> throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.not-support", docType);
        };

        // 原单标记 REVERSED（收付款无 status 列，仅落台账）+ 红冲台账
        String table = DocTypes.STATUS_TABLES.get(docType);
        if (table != null && docWriteMapper.markReversed(table, id, operator) == 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.status-conflict", docType, id);
        }
        BizReverseLog reverseLog = new BizReverseLog();
        reverseLog.setSourceType(docType);
        reverseLog.setSourceId(id);
        reverseLog.setSourceNo(sourceNoOf(docType, id));
        reverseLog.setReverseType(docType);
        reverseLog.setReverseId(result.getReverseDocId());
        reverseLog.setReverseNo(result.getReverseDocNo());
        reverseLog.setReason(reason);
        reverseLog.setReverseBy(operator);
        reverseLog.setReverseTime(LocalDateTime.now());
        reverseLogMapper.insert(reverseLog);
        log.info("[红冲][完成] 类型={} 原单id={} 红冲单={} 金额={} 操作人={}",
                docType, id, result.getReverseDocNo(),
                result.getReverseAmount() == null ? "-" : result.getReverseAmount().toPlainString(), operator);
        return result;
    }

    // ==================== 采购入库红冲 ====================

    private ReverseResultVO reversePurInstock(Long id, Long operator) {
        PurInstock doc = requireAudited(
                purInstockMapper.selectById(id), id, PurInstock.STATUS_AUDITED);
        List<PurInstockDetail> details = purInstockDetailMapper.selectList(
                Wrappers.lambdaQuery(PurInstockDetail.class).eq(PurInstockDetail::getInstockId, id));

        PurInstock red = new PurInstock();
        red.setInstockNo(redNo(doc.getInstockNo()));
        red.setOrderId(doc.getOrderId());
        red.setSupplierId(doc.getSupplierId());
        red.setWarehouseId(doc.getWarehouseId());
        red.setInstockDate(LocalDate.now());
        red.setStatus(PurInstock.STATUS_AUDITED);
        red.setAuditBy(operator);
        red.setAuditTime(LocalDateTime.now());
        red.setRemark("红冲采购入库 " + doc.getInstockNo());
        red.setTotalAmount(doc.getTotalAmount().negate());
        purInstockMapper.insert(red);

        // 库存反向：按原单流水腿精确冲减（同批次同库位，一次性冲全部腿）
        reverseMovements(InvBizType.PUR_IN.getCode(), id, InvBizType.PUR_RET, red.getId(), red.getInstockNo(),
                "红冲采购入库 " + doc.getInstockNo());
        for (PurInstockDetail d : details) {
            // 成本冲减：与采购退货同口径（当前移动加权价出库）
            costService.onStockOut(d.getMaterialId(), doc.getWarehouseId(), "PUR_RET",
                    red.getInstockNo(), red.getId(), d.getQty(), LocalDate.now());
            // 订单累计入库数回退（守卫 >=0）
            if (docWriteMapper.adjustReceivedQty(d.getOrderDetailId(), d.getQty().negate()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.order-qty-conflict",
                        d.getOrderDetailId());
            }
            // 红冲明细（负数）
            PurInstockDetail rd = new PurInstockDetail();
            rd.setInstockId(red.getId());
            rd.setOrderDetailId(d.getOrderDetailId());
            rd.setMaterialId(d.getMaterialId());
            rd.setBatchNo(d.getBatchNo());
            rd.setQty(d.getQty().negate());
            rd.setPrice(d.getPrice());
            rd.setTaxRate(d.getTaxRate());
            rd.setAmount(d.getAmount().negate());
            rd.setReturnedQty(BigDecimal.ZERO);
            rd.setRemark("红冲 " + doc.getInstockNo());
            purInstockDetailMapper.insert(rd);
        }
        // 反向应付（负数冲减供应商未付余额）
        FinPayable payable = new FinPayable();
        payable.setSupplierId(doc.getSupplierId());
        payable.setBizNo(red.getInstockNo());
        payable.setAmount(doc.getTotalAmount().negate());
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setStatus(FinPayable.STATUS_UNSETTLED);
        payable.setRemark("红冲采购入库 " + doc.getInstockNo());
        purPayableMapper.insert(payable);
        // 订单进度回落（30已完成 → 20部分入库）
        purOrderService.refreshProgressStatus(doc.getOrderId());

        ReverseResultVO vo = new ReverseResultVO();
        vo.setDocType(DocTypes.PUR_INSTOCK);
        vo.setReverseDocId(red.getId());
        vo.setReverseDocNo(red.getInstockNo());
        vo.setReverseAmount(red.getTotalAmount());
        return vo;
    }

    // ==================== 销售出库红冲 ====================

    private ReverseResultVO reverseSalOutstock(Long id, Long operator) {
        SalOutstock doc = requireAudited(salOutstockMapper.selectById(id), id, 10);
        List<SalOutstockDetail> details = salOutstockDetailMapper.selectList(
                Wrappers.lambdaQuery(SalOutstockDetail.class).eq(SalOutstockDetail::getOutId, id));

        SalOutstock red = new SalOutstock();
        red.setOutNo(redNo(doc.getOutNo()));
        red.setOrderId(doc.getOrderId());
        red.setOrderNo(doc.getOrderNo());
        red.setCustomerId(doc.getCustomerId());
        red.setWarehouseId(doc.getWarehouseId());
        red.setOutDate(LocalDate.now());
        red.setTotalQty(doc.getTotalQty().negate());
        red.setTotalAmount(doc.getTotalAmount().negate());
        red.setStatus(10);
        red.setRemark("红冲销售出库 " + doc.getOutNo());
        red.setAuditBy(operator);
        red.setAuditTime(LocalDateTime.now());
        salOutstockMapper.insert(red);

        reverseMovements(InvBizType.SAL_OUT.getCode(), id, InvBizType.SAL_RET, red.getId(), red.getOutNo(),
                "红冲销售出库 " + doc.getOutNo());
        for (SalOutstockDetail d : details) {
            costService.onStockIn(d.getMaterialId(), doc.getWarehouseId(), "SAL_RET",
                    red.getOutNo(), red.getId(), d.getQty(), null, LocalDate.now());
            if (docWriteMapper.adjustOutQty(d.getOrderDetailId(), d.getQty().negate()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.order-qty-conflict",
                        d.getOrderDetailId());
            }
            SalOutstockDetail rd = new SalOutstockDetail();
            rd.setOutId(red.getId());
            rd.setOrderDetailId(d.getOrderDetailId());
            rd.setMaterialId(d.getMaterialId());
            rd.setQty(d.getQty().negate());
            rd.setPrice(d.getPrice());
            rd.setAmount(d.getAmount().negate());
            rd.setReturnedQty(BigDecimal.ZERO);
            rd.setRemark("红冲 " + doc.getOutNo());
            salOutstockDetailMapper.insert(rd);
        }
        // 红字应收（负金额，与销售退货红字同口径）
        salesBizMapper.insertReceivable(doc.getCustomerId(), red.getOutNo(), LocalDate.now(),
                doc.getTotalAmount().negate(), "UNSETTLED", "红冲销售出库 " + doc.getOutNo());

        ReverseResultVO vo = new ReverseResultVO();
        vo.setDocType(DocTypes.SAL_OUTSTOCK);
        vo.setReverseDocId(red.getId());
        vo.setReverseDocNo(red.getOutNo());
        vo.setReverseAmount(red.getTotalAmount());
        return vo;
    }

    // ==================== 采购退货红冲 ====================

    private ReverseResultVO reversePurReturn(Long id, Long operator) {
        PurReturn doc = requireAudited(purReturnMapper.selectById(id), id, PurReturn.STATUS_AUDITED);
        List<PurReturnDetail> details = purReturnDetailMapper.selectList(
                Wrappers.lambdaQuery(PurReturnDetail.class).eq(PurReturnDetail::getReturnId, id));

        PurReturn red = new PurReturn();
        red.setReturnNo(redNo(doc.getReturnNo()));
        red.setInstockId(doc.getInstockId());
        red.setSupplierId(doc.getSupplierId());
        red.setWarehouseId(doc.getWarehouseId());
        red.setReturnDate(LocalDate.now());
        red.setTotalAmount(doc.getTotalAmount().negate());
        red.setStatus(PurReturn.STATUS_AUDITED);
        red.setAuditBy(operator);
        red.setAuditTime(LocalDateTime.now());
        red.setRemark("红冲采购退货 " + doc.getReturnNo());
        purReturnMapper.insert(red);

        reverseMovements(InvBizType.PUR_RET.getCode(), id, InvBizType.PUR_IN, red.getId(), red.getReturnNo(),
                "红冲采购退货 " + doc.getReturnNo());
        for (PurReturnDetail d : details) {
            PurInstockDetail src = purInstockDetailMapper.selectById(d.getInstockDetailId());
            if (src == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.biz.reverse.doc-not-exists",
                        d.getInstockDetailId());
            }
            costService.onStockIn(d.getMaterialId(), doc.getWarehouseId(), "PUR_RET",
                    red.getReturnNo(), red.getId(), d.getQty(), null, LocalDate.now());
            // 明细已退数量回退 + 订单累计入库数回加（守卫）
            if (docWriteMapper.adjustReturnedQty("pur_instock_detail", d.getInstockDetailId(), d.getQty().negate()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.returned-qty-conflict",
                        d.getInstockDetailId());
            }
            if (docWriteMapper.adjustReceivedQty(src.getOrderDetailId(), d.getQty()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.order-qty-conflict",
                        src.getOrderDetailId());
            }
            PurReturnDetail rd = new PurReturnDetail();
            rd.setReturnId(red.getId());
            rd.setInstockDetailId(d.getInstockDetailId());
            rd.setMaterialId(d.getMaterialId());
            rd.setQty(d.getQty().negate());
            rd.setPrice(d.getPrice());
            rd.setTaxRate(d.getTaxRate());
            rd.setAmount(d.getAmount().negate());
            rd.setRemark("红冲 " + doc.getReturnNo());
            purReturnDetailMapper.insert(rd);
        }
        purOrderService.refreshProgressStatus(
                requireAudited(purInstockMapper.selectById(doc.getInstockId()), doc.getInstockId(),
                        PurInstock.STATUS_AUDITED).getOrderId());

        ReverseResultVO vo = new ReverseResultVO();
        vo.setDocType(DocTypes.PUR_RETURN);
        vo.setReverseDocId(red.getId());
        vo.setReverseDocNo(red.getReturnNo());
        vo.setReverseAmount(red.getTotalAmount());
        return vo;
    }

    // ==================== 销售退货红冲 ====================

    private ReverseResultVO reverseSalReturn(Long id, Long operator) {
        SalReturn doc = requireAudited(salReturnMapper.selectById(id), id, 10);
        List<SalReturnDetail> details = salReturnDetailMapper.selectList(
                Wrappers.lambdaQuery(SalReturnDetail.class).eq(SalReturnDetail::getReturnId, id));

        SalReturn red = new SalReturn();
        red.setReturnNo(redNo(doc.getReturnNo()));
        red.setOutId(doc.getOutId());
        red.setOutNo(doc.getOutNo());
        red.setCustomerId(doc.getCustomerId());
        red.setWarehouseId(doc.getWarehouseId());
        red.setReturnDate(LocalDate.now());
        red.setTotalQty(doc.getTotalQty().negate());
        red.setTotalAmount(doc.getTotalAmount().negate());
        red.setStatus(10);
        red.setRemark("红冲销售退货 " + doc.getReturnNo());
        red.setAuditBy(operator);
        red.setAuditTime(LocalDateTime.now());
        salReturnMapper.insert(red);

        reverseMovements(InvBizType.SAL_RET.getCode(), id, InvBizType.SAL_OUT, red.getId(), red.getReturnNo(),
                "红冲销售退货 " + doc.getReturnNo());
        for (SalReturnDetail d : details) {
            costService.onStockOut(d.getMaterialId(), doc.getWarehouseId(), "SAL_RET",
                    red.getReturnNo(), red.getId(), d.getQty(), LocalDate.now());
            // 出库明细已退数量回退 + 订单累计出库数回加
            if (docWriteMapper.adjustReturnedQty("sal_outstock_detail", d.getOutDetailId(), d.getQty().negate()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.returned-qty-conflict",
                        d.getOutDetailId());
            }
            SalOutstockDetail src = salOutstockDetailMapper.selectById(d.getOutDetailId());
            if (src != null && docWriteMapper.adjustOutQty(src.getOrderDetailId(), d.getQty()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.order-qty-conflict",
                        src.getOrderDetailId());
            }
            SalReturnDetail rd = new SalReturnDetail();
            rd.setReturnId(red.getId());
            rd.setOutDetailId(d.getOutDetailId());
            rd.setMaterialId(d.getMaterialId());
            rd.setBatchNo(d.getBatchNo());
            rd.setQty(d.getQty().negate());
            rd.setPrice(d.getPrice());
            rd.setAmount(d.getAmount().negate());
            rd.setRemark("红冲 " + doc.getReturnNo());
            salReturnDetailMapper.insert(rd);
        }
        // 蓝字应收对冲（原退货生成的是负应收，红冲回加正数）
        salesBizMapper.insertReceivable(doc.getCustomerId(), red.getReturnNo(), LocalDate.now(),
                doc.getTotalAmount(), "UNSETTLED", "红冲销售退货 " + doc.getReturnNo());

        ReverseResultVO vo = new ReverseResultVO();
        vo.setDocType(DocTypes.SAL_RETURN);
        vo.setReverseDocId(red.getId());
        vo.setReverseDocNo(red.getReturnNo());
        vo.setReverseAmount(red.getTotalAmount());
        return vo;
    }

    // ==================== 收款单红冲 ====================

    private ReverseResultVO reverseReceipt(Long id, Long operator) {
        FinReceipt doc = requireAudited(finReceiptMapper.selectById(id), id, null);
        if (doc.getAmount() == null || doc.getAmount().signum() <= 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.is-red-doc");
        }
        List<FinReceiptItem> items = finReceiptItemMapper.selectList(
                Wrappers.lambdaQuery(FinReceiptItem.class).eq(FinReceiptItem::getReceiptId, id));

        // 1) 应收核销回冲（settled_amount 守卫扣减，0 行=已被后续业务占用）
        for (FinReceiptItem item : items) {
            if (docWriteMapper.unverifyReceivable(item.getReceivableId(), item.getVerifyAmount()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.settle-conflict",
                        item.getReceivableId());
            }
        }
        // 2) 账户余额反向扣减（余额不足整体回滚）
        if (finAccountMapper.changeBalance(doc.getBookId(), doc.getAccountId(),
                doc.getAmount().negate(), doc.getAmount()) == 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.account-balance-not-enough");
        }
        // 3) 红冲收款单（负数）+ 明细
        String redNo = redNo(doc.getReceiptNo());
        FinReceipt red = new FinReceipt();
        red.setBookId(doc.getBookId());
        red.setReceiptNo(redNo);
        red.setCustomerId(doc.getCustomerId());
        red.setAccountId(doc.getAccountId());
        red.setBizDate(LocalDate.now());
        red.setAmount(doc.getAmount().negate());
        red.setVerifiedAmount(doc.getAmount().negate());
        red.setRemark("红冲收款 " + doc.getReceiptNo());
        finReceiptMapper.insert(red);
        for (FinReceiptItem item : items) {
            FinReceiptItem ri = new FinReceiptItem();
            ri.setBookId(doc.getBookId());
            ri.setReceiptId(red.getId());
            ri.setReceivableId(item.getReceivableId());
            ri.setVerifyAmount(item.getVerifyAmount().negate());
            finReceiptItemMapper.insert(ri);
        }
        // 4) 账户流水（资金退出账户）
        FinAccount after = finAccountMapper.selectById(doc.getAccountId());
        FinAccountFlow flow = new FinAccountFlow();
        flow.setBookId(doc.getBookId());
        flow.setAccountId(doc.getAccountId());
        flow.setBizDate(LocalDate.now());
        flow.setBizType("RECEIPT_REVERSE");
        flow.setBizId(red.getId());
        flow.setBizNo(redNo);
        flow.setDirection("EXPENSE");
        flow.setAmount(doc.getAmount());
        flow.setBalanceAfter(after == null ? null : after.getCurrentBalance());
        flow.setRemark("红冲收款核销：" + redNo);
        finAccountFlowMapper.insert(flow);

        ReverseResultVO vo = new ReverseResultVO();
        vo.setDocType(DocTypes.RECEIPT);
        vo.setReverseDocId(red.getId());
        vo.setReverseDocNo(redNo);
        vo.setReverseAmount(red.getAmount());
        return vo;
    }

    // ==================== 付款单红冲 ====================

    private ReverseResultVO reversePayment(Long id, Long operator) {
        FinPayment doc = requireAudited(finPaymentMapper.selectById(id), id, null);
        if (doc.getAmount() == null || doc.getAmount().signum() <= 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.is-red-doc");
        }
        List<FinPaymentItem> items = finPaymentItemMapper.selectList(
                Wrappers.lambdaQuery(FinPaymentItem.class).eq(FinPaymentItem::getPaymentId, id));

        for (FinPaymentItem item : items) {
            if (docWriteMapper.unverifyPayable(item.getPayableId(), item.getVerifyAmount()) == 0) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.settle-conflict",
                        item.getPayableId());
            }
        }
        // 账户余额回加
        if (finAccountMapper.changeBalance(doc.getBookId(), doc.getAccountId(),
                doc.getAmount(), doc.getAmount()) == 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.account-change-failed");
        }
        String redNo = redNo(doc.getPaymentNo());
        FinPayment red = new FinPayment();
        red.setBookId(doc.getBookId());
        red.setPaymentNo(redNo);
        red.setSupplierId(doc.getSupplierId());
        red.setAccountId(doc.getAccountId());
        red.setBizDate(LocalDate.now());
        red.setAmount(doc.getAmount().negate());
        red.setVerifiedAmount(doc.getAmount().negate());
        red.setRemark("红冲付款 " + doc.getPaymentNo());
        finPaymentMapper.insert(red);
        for (FinPaymentItem item : items) {
            FinPaymentItem pi = new FinPaymentItem();
            pi.setBookId(doc.getBookId());
            pi.setPaymentId(red.getId());
            pi.setPayableId(item.getPayableId());
            pi.setVerifyAmount(item.getVerifyAmount().negate());
            finPaymentItemMapper.insert(pi);
        }
        FinAccount after = finAccountMapper.selectById(doc.getAccountId());
        FinAccountFlow flow = new FinAccountFlow();
        flow.setBookId(doc.getBookId());
        flow.setAccountId(doc.getAccountId());
        flow.setBizDate(LocalDate.now());
        flow.setBizType("PAYMENT_REVERSE");
        flow.setBizId(red.getId());
        flow.setBizNo(redNo);
        flow.setDirection("INCOME");
        flow.setAmount(doc.getAmount());
        flow.setBalanceAfter(after == null ? null : after.getCurrentBalance());
        flow.setRemark("红冲付款核销：" + redNo);
        finAccountFlowMapper.insert(flow);

        ReverseResultVO vo = new ReverseResultVO();
        vo.setDocType(DocTypes.PAYMENT);
        vo.setReverseDocId(red.getId());
        vo.setReverseDocNo(redNo);
        vo.setReverseAmount(red.getAmount());
        return vo;
    }

    // ==================== 公共工具 ====================

    /**
     * 按原单库存流水腿精确反向：入腿（qty&gt;0）→ 同批次同库位出库；
     * 出腿（qty&lt;0）→ 同批次同库位入库。任一腿库存不足整体回滚。
     */
    private void reverseMovements(String srcBizType, Long srcBizId, InvBizType reverseType,
                                  Long redDocId, String redNo, String remark) {
        List<InvMovement> legs = invMovementMapper.selectList(
                Wrappers.lambdaQuery(InvMovement.class)
                        .eq(InvMovement::getBizType, srcBizType)
                        .eq(InvMovement::getBizId, srcBizId));
        for (InvMovement leg : legs) {
            BigDecimal qty = leg.getQty();
            long location = leg.getLocationId() == null ? 0L : leg.getLocationId();
            if (qty.signum() > 0) {
                stockService.stockOutAtLocation(leg.getWarehouseId(), leg.getMaterialId(),
                        leg.getBatchNo(), location, qty, reverseType, redDocId, redNo, remark);
            } else if (qty.signum() < 0) {
                stockService.stockInAt(leg.getWarehouseId(), leg.getMaterialId(),
                        leg.getBatchNo(), location, qty.negate(), reverseType, redDocId, redNo, remark, null);
            }
        }
    }

    /** 未红冲 / 非红冲单守卫（双向查 biz_reverse_log） */
    private void guardNotReversed(String docType, Long id) {
        Long asSource = reverseLogMapper.selectCount(Wrappers.lambdaQuery(BizReverseLog.class)
                .eq(BizReverseLog::getSourceType, docType)
                .eq(BizReverseLog::getSourceId, id));
        if (asSource != null && asSource > 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.already-reversed");
        }
        Long asRed = reverseLogMapper.selectCount(Wrappers.lambdaQuery(BizReverseLog.class)
                .eq(BizReverseLog::getReverseType, docType)
                .eq(BizReverseLog::getReverseId, id));
        if (asRed != null && asRed > 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.is-red-doc");
        }
    }

    private <T> T requireAudited(T doc, Long id, Integer auditedStatus) {
        if (doc == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.biz.reverse.doc-not-exists", id);
        }
        if (auditedStatus != null) {
            Integer status = statusOf(doc);
            if (status == null || status != auditedStatus) {
                throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.reverse.doc-not-audited", id);
            }
        }
        return doc;
    }

    private Integer statusOf(Object doc) {
        return switch (doc) {
            case PurInstock d -> d.getStatus();
            case PurReturn d -> d.getStatus();
            case SalOutstock d -> d.getStatus();
            case SalReturn d -> d.getStatus();
            default -> null;
        };
    }

    private String sourceNoOf(String docType, Long id) {
        return switch (docType) {
            case DocTypes.PUR_INSTOCK -> purInstockMapper.selectById(id).getInstockNo();
            case DocTypes.SAL_OUTSTOCK -> salOutstockMapper.selectById(id).getOutNo();
            case DocTypes.PUR_RETURN -> purReturnMapper.selectById(id).getReturnNo();
            case DocTypes.SAL_RETURN -> salReturnMapper.selectById(id).getReturnNo();
            case DocTypes.RECEIPT -> finReceiptMapper.selectById(id).getReceiptNo();
            case DocTypes.PAYMENT -> finPaymentMapper.selectById(id).getPaymentNo();
            default -> null;
        };
    }

    private String redNo(String sourceNo) {
        return sourceNo + "R";
    }
}
