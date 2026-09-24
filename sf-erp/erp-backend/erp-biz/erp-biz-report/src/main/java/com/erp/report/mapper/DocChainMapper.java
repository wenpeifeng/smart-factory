package com.erp.report.mapper;

import com.erp.report.dto.vo.DocChainNodeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 单据联查 Mapper（XML：resources/mapper/report/DocChainMapper.xml）。
 * <p>
 * 跨模块<b>只读</b>邻接查询：每条语句回答"某单据的上/下游直接邻居是谁"，
 * 链路展开（BFS）由 DocChainService 编排。列别名统一 doc_id/doc_no/biz_date/amount/status。
 * <p>
 * 邻接图（→ 下游）：
 * sal_quotation → sal_order → sal_outstock → {sal_return, inv_sales_invoice, fin_receivable, fin_voucher}
 * sal_return → fin_receivable(红字)/fin_voucher；fin_receivable → fin_receipt → fin_voucher；
 * pur_order → pur_instock → {pur_return, inv_purchase_invoice, fin_payable, fin_voucher}
 * pur_return → fin_voucher；fin_payable → fin_payment → fin_voucher；
 * biz_reverse_log：原单 ↔ 红冲单。
 */
@Mapper
public interface DocChainMapper {

    // ==================== 锚点单据 ====================

    DocChainNodeVO selectQuotation(@Param("id") Long id);

    DocChainNodeVO selectSalOrder(@Param("id") Long id);

    DocChainNodeVO selectOutstock(@Param("id") Long id);

    DocChainNodeVO selectSalReturn(@Param("id") Long id);

    DocChainNodeVO selectPurOrder(@Param("id") Long id);

    DocChainNodeVO selectInstock(@Param("id") Long id);

    DocChainNodeVO selectPurReturn(@Param("id") Long id);

    DocChainNodeVO selectSalesInvoice(@Param("id") Long id);

    DocChainNodeVO selectPurchaseInvoice(@Param("id") Long id);

    DocChainNodeVO selectReceivable(@Param("id") Long id);

    DocChainNodeVO selectPayable(@Param("id") Long id);

    DocChainNodeVO selectReceipt(@Param("id") Long id);

    DocChainNodeVO selectPayment(@Param("id") Long id);

    DocChainNodeVO selectVoucher(@Param("id") Long id);

    // ==================== 上游（向业务源头方向） ====================

    /** 销售订单 → 来源报价单（sal_quotation.order_id） */
    List<DocChainNodeVO> upQuotationsFromOrder(@Param("orderId") Long orderId);

    /** 销售出库单 → 来源销售订单 */
    List<DocChainNodeVO> upOrderFromOutstock(@Param("outstockId") Long outstockId);

    /** 销售退货单 → 来源出库单 */
    List<DocChainNodeVO> upOutstockFromSalReturn(@Param("returnId") Long returnId);

    /** 采购入库单 → 来源采购订单 */
    List<DocChainNodeVO> upOrderFromInstock(@Param("instockId") Long instockId);

    /** 采购退货单 → 来源入库单 */
    List<DocChainNodeVO> upInstockFromPurReturn(@Param("returnId") Long returnId);

    /** 销售发票 → 来源出库单（biz_type=SAL_OUTSTOCK） */
    List<DocChainNodeVO> upOutstockFromSalesInvoice(@Param("invoiceId") Long invoiceId);

    /** 采购发票 → 来源入库单（biz_type=PUR_INSTOCK） */
    List<DocChainNodeVO> upInstockFromPurchaseInvoice(@Param("invoiceId") Long invoiceId);

    /** 应收单 → 来源出库单（biz_no = out_no，含红字应收） */
    List<DocChainNodeVO> upOutstockFromReceivable(@Param("receivableId") Long receivableId);

    /** 应收单 → 来源销售退货单（红字应收 biz_no = return_no） */
    List<DocChainNodeVO> upSalReturnFromReceivable(@Param("receivableId") Long receivableId);

    /** 应付单 → 来源入库单 */
    List<DocChainNodeVO> upInstockFromPayable(@Param("payableId") Long payableId);

    /** 付款单 → 核销的多张应付单（fin_payment_item） */
    List<DocChainNodeVO> upPayablesFromPayment(@Param("paymentId") Long paymentId);

    /** 收款单 → 核销的多张应收单（fin_receipt_item） */
    List<DocChainNodeVO> upReceivablesFromReceipt(@Param("receiptId") Long receiptId);

    /** 凭证 → 来源业务单据（按 fin_voucher.source_type/source_id，UNION 各表） */
    List<DocChainNodeVO> upSourcesFromVoucher(@Param("voucherId") Long voucherId);

    // ==================== 下游（向发票/资金/凭证方向） ====================

    /** 报价单 → 转出的销售订单 */
    List<DocChainNodeVO> downOrderFromQuotation(@Param("quotationId") Long quotationId);

    /** 销售订单 → 出库单（一单多次出库） */
    List<DocChainNodeVO> downOutstocksFromOrder(@Param("orderId") Long orderId);

    /** 出库单 → 销售退货单 */
    List<DocChainNodeVO> downSalReturnsFromOutstock(@Param("outstockId") Long outstockId);

    /** 销售订单 → ？（保留：订单行经发货通知出库，通知单不参与链路一期范围） */

    /** 出库单 → 销售发票 */
    List<DocChainNodeVO> downSalesInvoicesFromOutstock(@Param("outstockId") Long outstockId);

    /** 出库单 → 应收单（蓝字） */
    List<DocChainNodeVO> downReceivablesFromOutstock(@Param("outstockId") Long outstockId);

    /** 销售退货单 → 红字应收单 */
    List<DocChainNodeVO> downReceivablesFromSalReturn(@Param("returnId") Long returnId);

    /** 采购订单 → 入库单 */
    List<DocChainNodeVO> downInstocksFromOrder(@Param("orderId") Long orderId);

    /** 入库单 → 采购退货单 */
    List<DocChainNodeVO> downPurReturnsFromInstock(@Param("instockId") Long instockId);

    /** 入库单 → 采购发票 */
    List<DocChainNodeVO> downPurchaseInvoicesFromInstock(@Param("instockId") Long instockId);

    /** 入库单 → 应付单 */
    List<DocChainNodeVO> downPayablesFromInstock(@Param("instockId") Long instockId);

    /** 应收单 → 收款单（多次核销多张收款单） */
    List<DocChainNodeVO> downReceiptsFromReceivable(@Param("receivableId") Long receivableId);

    /** 应付单 → 付款单 */
    List<DocChainNodeVO> downPaymentsFromPayable(@Param("payableId") Long payableId);

    /** 业务单据 → 凭证（fin_voucher.source_type/source_id，sourceType 为凭证侧编码） */
    List<DocChainNodeVO> downVouchersFromSource(@Param("sourceType") String sourceType,
                                                @Param("sourceId") Long sourceId);

    // ==================== 红冲配对（biz_reverse_log） ====================

    /** 原单 → 红冲单（负数反向单节点，金额取负展示） */
    List<DocChainNodeVO> downRedDocFrom(@Param("sourceType") String sourceType,
                                        @Param("sourceId") Long sourceId);

    /** 红冲单 → 原单 */
    List<DocChainNodeVO> upSourceDocFromRed(@Param("reverseType") String reverseType,
                                            @Param("reverseId") Long reverseId);
}
